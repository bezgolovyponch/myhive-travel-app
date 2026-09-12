# Contacts Capture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every email address a visitor types anywhere on the site is kept permanently in one `contacts` table that sales/ops can browse and export from the admin console.

**Architecture:** A new `Contact` entity (one row per normalized email) is upserted by `ContactService.touch(...)` from the five places an address enters the system: Trip Builder lead capture, vote-session creation, booking creation, the contact form, and the Stripe payment webhook. `touch` runs in its own `REQUIRES_NEW` transaction and never throws, so a failed contact write cannot break the flow that captured the address. Existing `trip_leads` keep their 30-day purge; `email_suppressions` stays the source of truth for "do not email" and is surfaced as a flag on each contact. A Flyway migration creates the table on prod and backfills it from `bookings`, `vote_sessions`, `trip_leads` and `booking_payment_shares`. Admin gets `GET /admin/contacts` (paged, searchable) and `GET /admin/contacts/export` (CSV), both ADMIN-only, plus an `/admin/contacts` page.

**Tech Stack:** Spring Boot 4.0 / Java 25 / JPA (H2 in dev+test, Postgres 18 prod) / Flyway (prod only) / opencsv / React 19 CRA (Bootstrap 5) / Jest + RTL.

**Spec:** Agreed in chat on 2026-09-12 (no separate spec file). Decisions: operational/sales use only (legitimate interest, no consent checkbox, no marketing flag); contacts are never auto-deleted; ADMIN-only visibility.

## Global Constraints

- Google Java Style per `CLAUDE.md`: no wildcard imports, `@Override` everywhere, braces on every `if/for`, one variable per declaration, constants `UPPER_SNAKE_CASE`.
- Never log a raw email: wrap with `EmailMasker.mask(...)`.
- Email normalization = `trim()` + `toLowerCase(Locale.ROOT)`; null/blank must be a no-op, never a stored row.
- Locale normalization = `Translations.normalize(locale)` (returns `null` for English).
- Test style per `CLAUDE.md`: `expected`-prefixed variables shared between arrange and assert; DTOs built inline when field values matter.
- Tests that go through `ContactService.touch` must NOT be `@Transactional` (REQUIRES_NEW commits outside the test transaction) and must use unique emails (`"c-" + UUID.randomUUID() + "@example.com"`) plus explicit cleanup of the contacts they create.
- Flyway migrations run on prod only; H2 dev/test schemas come from Hibernate `create-drop`.
- Backend build/test: `cd myhive-backend && ./gradlew test --tests '*ContactServiceTest'`. Frontend tests: `cd myhive-react-app && CI=true npm test -- --watchAll=false AdminContacts`.
- Commit after every task with the attribution trailer given in the session.

---

## File Structure

| File | Responsibility |
|------|----------------|
| `myhive-backend/src/main/java/com/myhive/backend/model/ContactSource.java` | Enum of capture points: `TRIP_BUILDER`, `VOTE`, `BOOKING`, `CONTACT_FORM`, `PAYMENT` |
| `myhive-backend/src/main/java/com/myhive/backend/entity/Contact.java` | JPA entity for `contacts` |
| `myhive-backend/src/main/java/com/myhive/backend/repository/ContactRepository.java` | `findByEmail`, paged search |
| `myhive-backend/src/main/java/com/myhive/backend/repository/EmailSuppressionRepository.java` | + `findByEmailIn` (batch suppression lookup) |
| `myhive-backend/src/main/resources/db/migration/V5__contacts.sql` | Create table + backfill from existing tables (prod) |
| `myhive-backend/src/main/java/com/myhive/backend/service/ContactService.java` | `touch(...)` upsert (own transaction, never throws) + `search(...)` for admin |
| `myhive-backend/src/main/java/com/myhive/backend/dto/ContactDTO.java` | Admin read model incl. `unsubscribed` flag |
| `myhive-backend/src/main/java/com/myhive/backend/util/CsvCells.java` | Shared `nullSafe` / `sanitize` extracted from `ActivityCsvExporter` |
| `myhive-backend/src/main/java/com/myhive/backend/service/ContactCsvExporter.java` | CSV export of all contacts |
| `myhive-backend/src/main/java/com/myhive/backend/controller/AdminController.java` | + `GET /admin/contacts`, `GET /admin/contacts/export` |
| `myhive-backend/src/main/java/com/myhive/backend/config/SecurityConfig.java` | + explicit `/admin/contacts/**` → ADMIN |
| Call sites: `TripLeadService`, `VoteSessionService`, `BookingService`, `ContactController`, `PaymentService` | one `contactService.touch(...)` each |
| `myhive-react-app/src/services/adminApi.js` | + `getContactsPaged`, `exportContactsCsv` (shared `downloadCsv` helper) |
| `myhive-react-app/src/pages/AdminContacts.js` | Admin page: search, table, pagination, export |
| `myhive-react-app/src/AdminApp.js`, `components/AdminLayout.js` | Route + nav link (ADMIN only) |

---

### Task 1: `Contact` entity, `ContactSource`, repository, Flyway migration with backfill

**Files:**
- Create: `myhive-backend/src/main/java/com/myhive/backend/model/ContactSource.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/entity/Contact.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/repository/ContactRepository.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/repository/EmailSuppressionRepository.java`
- Create: `myhive-backend/src/main/resources/db/migration/V5__contacts.sql`
- Test: `myhive-backend/src/test/java/com/myhive/backend/repository/ContactRepositoryTest.java`

**Interfaces:**
- Produces: `enum ContactSource { TRIP_BUILDER, VOTE, BOOKING, CONTACT_FORM, PAYMENT }`
- Produces: `Contact` with getters/setters for `id`, `email`, `name`, `locale`, `firstSource`, `lastSource`, `firstSeenAt`, `lastSeenAt`, `touchCount`
- Produces: `ContactRepository.findByEmail(String): Optional<Contact>`, `ContactRepository.findByEmailContainingIgnoreCaseOrNameContainingIgnoreCase(String, String, Pageable): Page<Contact>`
- Produces: `EmailSuppressionRepository.findByEmailIn(Collection<String>): List<EmailSuppression>`

- [ ] **Step 1: Write the failing repository test**

`myhive-backend/src/test/java/com/myhive/backend/repository/ContactRepositoryTest.java`:

```java
package com.myhive.backend.repository;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.entity.Contact;
import com.myhive.backend.model.ContactSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@Import(TestSecurityConfig.class)
class ContactRepositoryTest {

    @Autowired private ContactRepository contactRepository;

    @Test
    void findByEmail_returnsSavedContact() {
        String expectedEmail = "c-" + UUID.randomUUID() + "@example.com";
        contactRepository.save(contact(expectedEmail, "Anna Example"));

        Contact found = contactRepository.findByEmail(expectedEmail).orElseThrow();

        assertThat(found.getEmail()).isEqualTo(expectedEmail);
        assertThat(found.getFirstSource()).isEqualTo(ContactSource.BOOKING);
        assertThat(found.getTouchCount()).isEqualTo(1);
    }

    @Test
    void search_matchesEmailOrNameCaseInsensitively() {
        String marker = UUID.randomUUID().toString().substring(0, 8);
        String expectedByEmail = "c-" + marker + "@example.com";
        String expectedByName = "c-" + UUID.randomUUID() + "@example.com";
        contactRepository.save(contact(expectedByEmail, null));
        contactRepository.save(contact(expectedByName, "Mr " + marker.toUpperCase()));
        contactRepository.save(contact("c-" + UUID.randomUUID() + "@example.com", "Nobody"));

        Page<Contact> page = contactRepository
                .findByEmailContainingIgnoreCaseOrNameContainingIgnoreCase(marker, marker, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(Contact::getEmail)
                .containsExactlyInAnyOrder(expectedByEmail, expectedByName);
    }

    private static Contact contact(String email, String name) {
        LocalDateTime now = LocalDateTime.now();
        Contact contact = new Contact();
        contact.setEmail(email);
        contact.setName(name);
        contact.setFirstSource(ContactSource.BOOKING);
        contact.setLastSource(ContactSource.BOOKING);
        contact.setFirstSeenAt(now);
        contact.setLastSeenAt(now);
        contact.setTouchCount(1);
        return contact;
    }
}
```

- [ ] **Step 2: Run it to confirm it fails to compile**

Run: `cd myhive-backend && ./gradlew test --tests '*ContactRepositoryTest'`
Expected: compilation error — `Contact`, `ContactSource`, `ContactRepository` do not exist.

- [ ] **Step 3: Create the enum**

`myhive-backend/src/main/java/com/myhive/backend/model/ContactSource.java`:

```java
package com.myhive.backend.model;

/** Where an email address entered the system. Stored as VARCHAR — never reorder or rename. */
public enum ContactSource {
    TRIP_BUILDER,
    VOTE,
    BOOKING,
    CONTACT_FORM,
    PAYMENT
}
```

- [ ] **Step 4: Create the entity**

`myhive-backend/src/main/java/com/myhive/backend/entity/Contact.java`:

```java
package com.myhive.backend.entity;

import com.myhive.backend.model.ContactSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row per email address ever typed into the site (Trip Builder, vote, booking, contact form,
 * payment). Rows are never auto-deleted — this is the sales/ops address book. Opt-out lives in
 * {@link EmailSuppression}, not here.
 */
@Entity
@Table(name = "contacts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    /** Normalized (trimmed, lowercase) — see ContactService.normalizeEmail. */
    @Column(unique = true, nullable = false)
    private String email;

    /** Latest non-blank name the visitor gave us (booking or contact form); null when never given. */
    private String name;

    /** Latest locale the visitor browsed in ("de"); null = English. */
    @Column(length = 8)
    private String locale;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "first_source", nullable = false, length = 20)
    private ContactSource firstSource;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "last_source", nullable = false, length = 20)
    private ContactSource lastSource;

    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    /** How many times any capture point saw this address. */
    @Column(name = "touch_count", nullable = false)
    private int touchCount;
}
```

- [ ] **Step 5: Create the repository and extend the suppression repository**

`myhive-backend/src/main/java/com/myhive/backend/repository/ContactRepository.java`:

```java
package com.myhive.backend.repository;

import com.myhive.backend.entity.Contact;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ContactRepository extends JpaRepository<Contact, UUID> {

    Optional<Contact> findByEmail(String email);

    /** Both parameters receive the same search term; an empty term matches every row via the email clause. */
    Page<Contact> findByEmailContainingIgnoreCaseOrNameContainingIgnoreCase(
            String email, String name, Pageable pageable);
}
```

Replace the body of `myhive-backend/src/main/java/com/myhive/backend/repository/EmailSuppressionRepository.java` with:

```java
package com.myhive.backend.repository;

import com.myhive.backend.entity.EmailSuppression;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface EmailSuppressionRepository extends JpaRepository<EmailSuppression, UUID> {

    boolean existsByEmail(String email);

    List<EmailSuppression> findByEmailIn(Collection<String> emails);
}
```

- [ ] **Step 6: Run the repository test**

Run: `cd myhive-backend && ./gradlew test --tests '*ContactRepositoryTest'`
Expected: PASS (2 tests).

- [ ] **Step 7: Write the Flyway migration (prod only)**

`myhive-backend/src/main/resources/db/migration/V5__contacts.sql`:

```sql
-- Sales/ops address book: one row per normalized email ever typed into the site.
-- Hibernate `update` would create the table too; shipping it here keeps prod DDL explicit
-- and lets us backfill from the tables that already hold addresses.
CREATE TABLE IF NOT EXISTS contacts (
    id            uuid PRIMARY KEY,
    email         varchar(255) NOT NULL,
    name          varchar(255),
    locale        varchar(8),
    first_source  varchar(20) NOT NULL,
    last_source   varchar(20) NOT NULL,
    first_seen_at timestamp(6) NOT NULL,
    last_seen_at  timestamp(6) NOT NULL,
    touch_count   integer NOT NULL DEFAULT 0,
    CONSTRAINT uk_contacts_email UNIQUE (email)
);

-- One-time backfill from every place that already stores an address. Idempotent via ON CONFLICT.
WITH touches AS (
    SELECT lower(trim(user_email)) AS email, customer_name AS name, locale,
           'BOOKING' AS source, created_at AS seen_at
    FROM bookings
    WHERE user_email IS NOT NULL AND trim(user_email) <> ''
    UNION ALL
    SELECT lower(trim(initiator_email)), NULL, locale,
           'VOTE', COALESCE(email_captured_at, created_at)
    FROM vote_sessions
    WHERE initiator_email IS NOT NULL AND trim(initiator_email) <> ''
    UNION ALL
    SELECT email, NULL, locale, 'TRIP_BUILDER', created_at
    FROM trip_leads
    UNION ALL
    SELECT lower(trim(payer_email)), NULL, NULL, 'PAYMENT', paid_at
    FROM booking_payment_shares
    WHERE payer_email IS NOT NULL AND trim(payer_email) <> ''
),
ranked AS (
    SELECT email, name, locale, source, seen_at,
           row_number() OVER (PARTITION BY email ORDER BY seen_at ASC NULLS LAST)  AS rn_first,
           row_number() OVER (PARTITION BY email ORDER BY seen_at DESC NULLS LAST) AS rn_last,
           count(*)     OVER (PARTITION BY email)                                  AS touches
    FROM touches
)
INSERT INTO contacts (id, email, name, locale, first_source, last_source, first_seen_at, last_seen_at, touch_count)
SELECT gen_random_uuid(),
       f.email,
       (SELECT max(t.name) FROM touches t WHERE t.email = f.email AND t.name IS NOT NULL AND trim(t.name) <> ''),
       COALESCE(l.locale, f.locale),
       f.source,
       l.source,
       COALESCE(f.seen_at, now()),
       COALESCE(l.seen_at, now()),
       f.touches
FROM ranked f
JOIN ranked l ON l.email = f.email AND l.rn_last = 1
WHERE f.rn_first = 1
ON CONFLICT (email) DO NOTHING;
```

- [ ] **Step 8: Dry-run the backfill SELECT against prod (read-only)**

Flyway is disabled in tests, so the SQL is never executed by the build. Before merging, run the `WITH touches ... SELECT ...` part (everything except the `INSERT INTO contacts (...)` line and the `ON CONFLICT` line) as a read-only query on the prod database (Render MCP `query_render_postgres`, or `psql` against the external URL) and confirm: it executes, row count looks like the number of distinct emails, and `first_source`/`last_source` values are only the five enum names.

- [ ] **Step 9: Run the full backend test suite**

Run: `cd myhive-backend && ./gradlew test`
Expected: all green (new entity must not break `SchemaColumnsTest` or context loading).

- [ ] **Step 10: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/model/ContactSource.java \
        myhive-backend/src/main/java/com/myhive/backend/entity/Contact.java \
        myhive-backend/src/main/java/com/myhive/backend/repository/ContactRepository.java \
        myhive-backend/src/main/java/com/myhive/backend/repository/EmailSuppressionRepository.java \
        myhive-backend/src/main/resources/db/migration/V5__contacts.sql \
        myhive-backend/src/test/java/com/myhive/backend/repository/ContactRepositoryTest.java
git commit -m "feat(contacts): Contact entity, repository and V5 migration with backfill"
```

---

### Task 2: `ContactService.touch` (own transaction, never throws) + `search`

**Files:**
- Create: `myhive-backend/src/main/java/com/myhive/backend/service/ContactService.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/dto/ContactDTO.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/ContactServiceTest.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/ContactServiceFailureTest.java`

**Interfaces:**
- Consumes: `ContactRepository`, `EmailSuppressionRepository.findByEmailIn`, `ContactSource`, `Translations.normalize`, `EmailMasker.mask`
- Produces: `void ContactService.touch(String rawEmail, ContactSource source, String name, String locale)` — null/blank email is a no-op; never throws.
- Produces: `Page<ContactDTO> ContactService.search(String query, Pageable pageable)`
- Produces: `ContactDTO(UUID id, String email, String name, String locale, ContactSource firstSource, ContactSource lastSource, LocalDateTime firstSeenAt, LocalDateTime lastSeenAt, int touchCount, boolean unsubscribed)`

- [ ] **Step 1: Write the failing integration test**

`myhive-backend/src/test/java/com/myhive/backend/service/ContactServiceTest.java`:

```java
package com.myhive.backend.service;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.dto.ContactDTO;
import com.myhive.backend.entity.Contact;
import com.myhive.backend.entity.EmailSuppression;
import com.myhive.backend.model.ContactSource;
import com.myhive.backend.repository.ContactRepository;
import com.myhive.backend.repository.EmailSuppressionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Not @Transactional: touch() commits in a REQUIRES_NEW transaction, which a test transaction
// could neither see nor roll back. Every test uses unique emails and cleans up after itself.
@SpringBootTest
@Import(TestSecurityConfig.class)
class ContactServiceTest {

    @Autowired private ContactService contactService;
    @Autowired private ContactRepository contactRepository;
    @Autowired private EmailSuppressionRepository emailSuppressionRepository;

    private final List<String> createdEmails = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (String email : createdEmails) {
            contactRepository.findByEmail(email).ifPresent(contactRepository::delete);
        }
    }

    private String uniqueEmail() {
        String email = "c-" + UUID.randomUUID() + "@example.com";
        createdEmails.add(email);
        return email;
    }

    @Test
    void touch_newAddress_createsNormalizedContact() {
        String expectedEmail = uniqueEmail();

        contactService.touch("  " + expectedEmail.toUpperCase() + " ", ContactSource.TRIP_BUILDER, null, "de");

        Contact contact = contactRepository.findByEmail(expectedEmail).orElseThrow();
        assertThat(contact.getFirstSource()).isEqualTo(ContactSource.TRIP_BUILDER);
        assertThat(contact.getLastSource()).isEqualTo(ContactSource.TRIP_BUILDER);
        assertThat(contact.getLocale()).isEqualTo("de");
        assertThat(contact.getTouchCount()).isEqualTo(1);
        assertThat(contact.getFirstSeenAt()).isEqualTo(contact.getLastSeenAt());
    }

    @Test
    void touch_existingAddress_updatesLastSeenAndKeepsFirst() {
        String email = uniqueEmail();
        contactService.touch(email, ContactSource.VOTE, null, null);
        Contact first = contactRepository.findByEmail(email).orElseThrow();

        contactService.touch(email, ContactSource.BOOKING, "Anna Example", "de");

        Contact updated = contactRepository.findByEmail(email).orElseThrow();
        assertThat(updated.getId()).isEqualTo(first.getId());
        assertThat(updated.getFirstSource()).isEqualTo(ContactSource.VOTE);
        assertThat(updated.getLastSource()).isEqualTo(ContactSource.BOOKING);
        assertThat(updated.getFirstSeenAt()).isEqualTo(first.getFirstSeenAt());
        assertThat(updated.getLastSeenAt()).isAfterOrEqualTo(first.getLastSeenAt());
        assertThat(updated.getName()).isEqualTo("Anna Example");
        assertThat(updated.getLocale()).isEqualTo("de");
        assertThat(updated.getTouchCount()).isEqualTo(2);
    }

    @Test
    void touch_blankNameAndEnglishLocale_keepExistingValues() {
        String email = uniqueEmail();
        contactService.touch(email, ContactSource.CONTACT_FORM, "Anna Example", "de");

        contactService.touch(email, ContactSource.PAYMENT, "   ", "en");

        Contact contact = contactRepository.findByEmail(email).orElseThrow();
        assertThat(contact.getName()).isEqualTo("Anna Example");
        assertThat(contact.getLocale()).isEqualTo("de");
    }

    @Test
    void touch_blankOrNullEmail_storesNothing() {
        long before = contactRepository.count();

        contactService.touch(null, ContactSource.BOOKING, "Anna", null);
        contactService.touch("   ", ContactSource.BOOKING, "Anna", null);

        assertThat(contactRepository.count()).isEqualTo(before);
    }

    @Test
    void search_flagsSuppressedAddresses() {
        String marker = UUID.randomUUID().toString().substring(0, 8);
        String expectedSuppressed = "c-" + marker + "-a@example.com";
        String expectedActive = "c-" + marker + "-b@example.com";
        createdEmails.add(expectedSuppressed);
        createdEmails.add(expectedActive);
        contactService.touch(expectedSuppressed, ContactSource.VOTE, null, null);
        contactService.touch(expectedActive, ContactSource.VOTE, null, null);
        EmailSuppression suppression = new EmailSuppression();
        suppression.setEmail(expectedSuppressed);
        emailSuppressionRepository.save(suppression);

        Page<ContactDTO> page = contactService.search(marker, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(ContactDTO::getEmail, ContactDTO::isUnsubscribed)
                .containsExactlyInAnyOrder(
                        tuple(expectedSuppressed, true),
                        tuple(expectedActive, false));
    }
}
```

(`tuple` comes from `import static org.assertj.core.api.Assertions.tuple;` — add it next to the `assertThat` import.)

- [ ] **Step 2: Write the failing unit test for the never-throws contract**

`myhive-backend/src/test/java/com/myhive/backend/service/ContactServiceFailureTest.java`:

```java
package com.myhive.backend.service;

import com.myhive.backend.model.ContactSource;
import com.myhive.backend.repository.ContactRepository;
import com.myhive.backend.repository.EmailSuppressionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContactServiceFailureTest {

    @Mock private ContactRepository contactRepository;
    @Mock private EmailSuppressionRepository emailSuppressionRepository;
    @Mock private PlatformTransactionManager transactionManager;

    @Test
    void touch_repositoryFailure_isSwallowedAndLogged() {
        when(contactRepository.findByEmail(anyString())).thenThrow(new IllegalStateException("db down"));
        ContactService service = new ContactService(contactRepository, emailSuppressionRepository, transactionManager);

        assertThatCode(() -> service.touch("anna@example.com", ContactSource.BOOKING, "Anna", null))
                .doesNotThrowAnyException();
    }
}
```

- [ ] **Step 3: Run both tests to confirm they fail to compile**

Run: `cd myhive-backend && ./gradlew test --tests '*ContactService*'`
Expected: compilation error — `ContactService`, `ContactDTO` do not exist.

- [ ] **Step 4: Create the DTO**

`myhive-backend/src/main/java/com/myhive/backend/dto/ContactDTO.java`:

```java
package com.myhive.backend.dto;

import com.myhive.backend.model.ContactSource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContactDTO {
    private UUID id;
    private String email;
    private String name;
    private String locale;
    private ContactSource firstSource;
    private ContactSource lastSource;
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
    private int touchCount;
    /** True when the address sits in email_suppressions — do not email this person. */
    private boolean unsubscribed;
}
```

- [ ] **Step 5: Create the service**

`myhive-backend/src/main/java/com/myhive/backend/service/ContactService.java`:

```java
package com.myhive.backend.service;

import com.myhive.backend.dto.ContactDTO;
import com.myhive.backend.entity.Contact;
import com.myhive.backend.entity.EmailSuppression;
import com.myhive.backend.model.ContactSource;
import com.myhive.backend.repository.ContactRepository;
import com.myhive.backend.repository.EmailSuppressionRepository;
import com.myhive.backend.util.EmailMasker;
import com.myhive.backend.util.Translations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The sales/ops address book. {@link #touch} is called from every place a visitor types an email
 * (Trip Builder lead, vote creation, booking, contact form, Stripe payment) and upserts one
 * {@link Contact} per normalized address.
 */
@Service
@Slf4j
public class ContactService {

    private final ContactRepository contactRepository;
    private final EmailSuppressionRepository emailSuppressionRepository;
    private final TransactionTemplate requiresNew;

    public ContactService(ContactRepository contactRepository,
                          EmailSuppressionRepository emailSuppressionRepository,
                          PlatformTransactionManager transactionManager) {
        this.contactRepository = contactRepository;
        this.emailSuppressionRepository = emailSuppressionRepository;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Records that {@code rawEmail} was entered at {@code source}. Runs in its own transaction and
     * never throws: a failed contact write must not break the flow that captured the address.
     * Null/blank emails are ignored; a blank {@code name} or an English/blank {@code locale} leaves
     * the stored value untouched.
     */
    public void touch(String rawEmail, ContactSource source, String name, String locale) {
        String email = normalizeEmail(rawEmail);
        if (email == null) {
            return;
        }
        try {
            requiresNew.executeWithoutResult(status -> upsert(email, source, name, locale));
        } catch (Exception e) {
            // Best-effort by contract — the caller's transaction must not see this failure.
            log.error("Failed to record contact {} from {}: {}",
                    EmailMasker.mask(email), source, e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public Page<ContactDTO> search(String query, Pageable pageable) {
        String term = query == null ? "" : query.trim();
        Page<Contact> page = contactRepository
                .findByEmailContainingIgnoreCaseOrNameContainingIgnoreCase(term, term, pageable);
        Set<String> suppressed = suppressedEmails(page.getContent());
        return page.map(contact -> toDto(contact, suppressed.contains(contact.getEmail())));
    }

    @Transactional(readOnly = true)
    public List<ContactDTO> findAllForExport() {
        List<Contact> contacts = contactRepository.findAll();
        Set<String> suppressed = suppressedEmails(contacts);
        return contacts.stream()
                .map(contact -> toDto(contact, suppressed.contains(contact.getEmail())))
                .toList();
    }

    private void upsert(String email, ContactSource source, String name, String locale) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Contact contact = contactRepository.findByEmail(email)
                .orElseGet(() -> newContact(email, source, now));
        contact.setLastSource(source);
        contact.setLastSeenAt(now);
        contact.setTouchCount(contact.getTouchCount() + 1);
        if (name != null && !name.isBlank()) {
            contact.setName(name.trim());
        }
        String normalizedLocale = Translations.normalize(locale);
        if (normalizedLocale != null) {
            contact.setLocale(normalizedLocale);
        }
        contactRepository.save(contact);
    }

    private static Contact newContact(String email, ContactSource source, LocalDateTime now) {
        Contact contact = new Contact();
        contact.setEmail(email);
        contact.setFirstSource(source);
        contact.setFirstSeenAt(now);
        contact.setTouchCount(0);
        return contact;
    }

    private Set<String> suppressedEmails(List<Contact> contacts) {
        List<String> emails = contacts.stream().map(Contact::getEmail).toList();
        if (emails.isEmpty()) {
            return Set.of();
        }
        return emailSuppressionRepository.findByEmailIn(emails).stream()
                .map(EmailSuppression::getEmail)
                .collect(Collectors.toSet());
    }

    private static ContactDTO toDto(Contact contact, boolean unsubscribed) {
        return new ContactDTO(contact.getId(), contact.getEmail(), contact.getName(), contact.getLocale(),
                contact.getFirstSource(), contact.getLastSource(), contact.getFirstSeenAt(),
                contact.getLastSeenAt(), contact.getTouchCount(), unsubscribed);
    }

    /** Trimmed + lower-cased, or null for null/blank — a blank address must never become a row. */
    static String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }
}
```

- [ ] **Step 6: Run both test classes**

Run: `cd myhive-backend && ./gradlew test --tests '*ContactService*'`
Expected: PASS (6 tests).

- [ ] **Step 7: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/service/ContactService.java \
        myhive-backend/src/main/java/com/myhive/backend/dto/ContactDTO.java \
        myhive-backend/src/test/java/com/myhive/backend/service/ContactServiceTest.java \
        myhive-backend/src/test/java/com/myhive/backend/service/ContactServiceFailureTest.java
git commit -m "feat(contacts): ContactService upsert in its own transaction, plus admin search"
```

---

### Task 3: Capture at Trip Builder lead, vote creation and booking creation

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/TripLeadService.java` (fields ~L52-59; `create` ~L66-85)
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/VoteSessionService.java` (fields ~L88; `newSession` ~L157-178)
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/BookingService.java` (fields ~L52-57; `createBooking` ~L88; `createBookingEntity` ~L222)
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/ContactCaptureIntegrationTest.java`

**Interfaces:**
- Consumes: `ContactService.touch(String, ContactSource, String, String)`
- Consumes (existing): `TripLeadService.create(TripLeadCreateRequest)`, `VoteSessionService.createSession(VoteSessionCreateRequest)`, `BookingService.createBooking(CreateBookingRequest)`, `BookingService.createBookingEntity(TripExportRequest, boolean)`, `TestDataFactory.destination(String)`, `TestDataFactory.activity(Destination, String, BigDecimal)`, `TestDataFactory.createBookingRequest(UUID)`, `TestDataFactory.tripExportRequest()`

- [ ] **Step 1: Write the failing integration test**

`myhive-backend/src/test/java/com/myhive/backend/service/ContactCaptureIntegrationTest.java`:

```java
package com.myhive.backend.service;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.dto.CreateBookingRequest;
import com.myhive.backend.dto.TripExportRequest;
import com.myhive.backend.dto.TripLeadCreateRequest;
import com.myhive.backend.dto.VoteSessionCreateRequest;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Contact;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.model.ContactSource;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.ContactRepository;
import com.myhive.backend.repository.DestinationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Not @Transactional: ContactService.touch commits in a REQUIRES_NEW transaction. Unique emails
// per test; contacts are deleted afterwards, catalog rows are left (as other non-transactional
// tests in this package do).
@SpringBootTest
@Import(TestSecurityConfig.class)
class ContactCaptureIntegrationTest {

    @Autowired private TripLeadService tripLeadService;
    @Autowired private VoteSessionService voteSessionService;
    @Autowired private BookingService bookingService;
    @Autowired private ContactRepository contactRepository;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private ActivityRepository activityRepository;

    private final List<String> createdEmails = new ArrayList<>();
    private Destination destination;
    private Activity activity;

    @BeforeEach
    void setUp() {
        destination = destinationRepository.saveAndFlush(TestDataFactory.destination("Prague"));
        activity = activityRepository.saveAndFlush(
                TestDataFactory.activity(destination, "Karting", new BigDecimal("50.00")));
    }

    @AfterEach
    void cleanUp() {
        for (String email : createdEmails) {
            contactRepository.findByEmail(email).ifPresent(contactRepository::delete);
        }
    }

    private String uniqueEmail() {
        String email = "c-" + UUID.randomUUID() + "@example.com";
        createdEmails.add(email);
        return email;
    }

    @Test
    void tripLeadCreate_recordsTripBuilderContact() {
        String expectedEmail = uniqueEmail();
        TripLeadCreateRequest request = new TripLeadCreateRequest();
        request.setEmail(expectedEmail);
        request.setDestinationId(destination.getId());
        request.setLocale("de");

        tripLeadService.create(request);

        Contact contact = contactRepository.findByEmail(expectedEmail).orElseThrow();
        assertThat(contact.getFirstSource()).isEqualTo(ContactSource.TRIP_BUILDER);
        assertThat(contact.getLocale()).isEqualTo("de");
    }

    @Test
    void voteSessionCreate_recordsVoteContact() {
        String expectedEmail = uniqueEmail();
        VoteSessionCreateRequest request = new VoteSessionCreateRequest();
        request.setDestinationId(destination.getId());
        request.setInitiatorEmail(expectedEmail);
        request.setNumberOfTravelers(2);
        request.setStartDate(LocalDate.of(2026, 10, 1));
        request.setEndDate(LocalDate.of(2026, 10, 3));
        request.setVoterToken(UUID.randomUUID());
        request.setQuizResponses(List.of());
        request.setActivityIds(List.of(activity.getId()));

        voteSessionService.createSession(request);

        Contact contact = contactRepository.findByEmail(expectedEmail).orElseThrow();
        assertThat(contact.getFirstSource()).isEqualTo(ContactSource.VOTE);
    }

    @Test
    void voteSessionCreate_withoutEmail_recordsNothing() {
        long before = contactRepository.count();
        VoteSessionCreateRequest request = new VoteSessionCreateRequest();
        request.setDestinationId(destination.getId());
        request.setInitiatorEmail(null);
        request.setNumberOfTravelers(2);
        request.setStartDate(LocalDate.of(2026, 10, 1));
        request.setEndDate(LocalDate.of(2026, 10, 3));
        request.setVoterToken(UUID.randomUUID());
        request.setQuizResponses(List.of());
        request.setActivityIds(List.of(activity.getId()));

        voteSessionService.createSession(request);

        assertThat(contactRepository.count()).isEqualTo(before);
    }

    @Test
    void createBooking_recordsBookingContact() {
        String expectedEmail = uniqueEmail();
        CreateBookingRequest request = TestDataFactory.createBookingRequest(activity.getId());
        request.setUserEmail(expectedEmail);

        bookingService.createBooking(request);

        Contact contact = contactRepository.findByEmail(expectedEmail).orElseThrow();
        assertThat(contact.getFirstSource()).isEqualTo(ContactSource.BOOKING);
    }

    @Test
    void createBookingEntity_recordsBookingContactWithCustomerName() {
        String expectedEmail = uniqueEmail();
        String expectedName = "Anna Example";
        TripExportRequest request = TestDataFactory.tripExportRequest();
        request.setUserEmail(expectedEmail);
        request.setCustomerName(expectedName);

        bookingService.createBookingEntity(request, false);

        Contact contact = contactRepository.findByEmail(expectedEmail).orElseThrow();
        assertThat(contact.getFirstSource()).isEqualTo(ContactSource.BOOKING);
        assertThat(contact.getName()).isEqualTo(expectedName);
    }
}
```

- [ ] **Step 2: Run it to confirm the five tests fail**

Run: `cd myhive-backend && ./gradlew test --tests '*ContactCaptureIntegrationTest'`
Expected: 4 failures with `NoSuchElementException` (contact missing); `voteSessionCreate_withoutEmail_recordsNothing` passes already.

- [ ] **Step 3: Wire `TripLeadService.create`**

In `TripLeadService`, add the import `com.myhive.backend.model.ContactSource` and the field (next to the other `private final` repositories):

```java
    private final ContactService contactService;
```

At the end of `create(...)`, replace

```java
        lead = tripLeadRepository.save(lead);
        return new TripLeadCreateResponse(lead.getId(), lead.getRestoreToken());
```

with

```java
        lead = tripLeadRepository.save(lead);
        contactService.touch(email, ContactSource.TRIP_BUILDER, null, lead.getLocale());
        return new TripLeadCreateResponse(lead.getId(), lead.getRestoreToken());
```

(`createFromVoteSession` is deliberately not wired: that address was already touched with source `VOTE` when the session was created.)

- [ ] **Step 4: Wire `VoteSessionService.newSession`**

In `VoteSessionService`, add the import `com.myhive.backend.model.ContactSource` and the field:

```java
    private final ContactService contactService;
```

In `newSession(...)`, replace the final line

```java
        return voteSessionRepository.save(session);
```

with

```java
        VoteSession saved = voteSessionRepository.save(session);
        contactService.touch(saved.getInitiatorEmail(), ContactSource.VOTE, null, saved.getLocale());
        return saved;
```

(`touch` ignores the null email of an API-created session, so no extra guard is needed.)

- [ ] **Step 5: Wire both booking factories in `BookingService`**

Add the import `com.myhive.backend.model.ContactSource` and the field:

```java
    private final ContactService contactService;
```

In `createBooking(...)`, replace

```java
        Booking savedBooking = bookingRepository.save(booking);
        return convertToDTO(savedBooking);
```

with

```java
        Booking savedBooking = bookingRepository.save(booking);
        recordContact(savedBooking);
        return convertToDTO(savedBooking);
```

In `createBookingEntity(...)`, directly after `Booking saved = bookingRepository.save(booking);` insert:

```java
        recordContact(saved);
```

Add the private helper next to the other private methods:

```java
    /** The booking's customer joins the sales address book; best-effort by ContactService contract. */
    private void recordContact(Booking booking) {
        contactService.touch(booking.getUserEmail(), ContactSource.BOOKING,
                booking.getCustomerName(), booking.getLocale());
    }
```

- [ ] **Step 6: Run the integration test and the existing suites for the touched services**

Run: `cd myhive-backend && ./gradlew test --tests '*ContactCaptureIntegrationTest' --tests '*TripLead*' --tests '*VoteSession*' --tests '*BookingService*'`
Expected: PASS. If any Mockito-based test constructs these services by hand (`new TripLeadService(...)` etc.), add a `@Mock private ContactService contactService;` and pass it — `grep -rn "new TripLeadService(\|new VoteSessionService(\|new BookingService(" myhive-backend/src/test` shows where.

- [ ] **Step 7: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/service/TripLeadService.java \
        myhive-backend/src/main/java/com/myhive/backend/service/VoteSessionService.java \
        myhive-backend/src/main/java/com/myhive/backend/service/BookingService.java \
        myhive-backend/src/test/java/com/myhive/backend/service/ContactCaptureIntegrationTest.java
git commit -m "feat(contacts): record contacts from trip leads, vote sessions and bookings"
```

---

### Task 4: Capture at the contact form

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/controller/ContactController.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/controller/ContactControllerTest.java`

**Interfaces:**
- Consumes: `ContactService.touch(String, ContactSource, String, String)`; `ContactRequest.getName()`, `getEmail()`

- [ ] **Step 1: Extend the controller test**

In `ContactControllerTest`, add imports:

```java
import com.myhive.backend.model.ContactSource;
import com.myhive.backend.service.ContactService;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
```

Add a third mock bean inside `MockConfig`:

```java
        @Bean
        @Primary
        public ContactService contactService() {
            return mock(ContactService.class);
        }
```

Autowire it and reset it in `setUp()`:

```java
    @Autowired
    private ContactService contactService;
    // in setUp():
        reset(contactService);
```

Add two tests:

```java
    @Test
    void submitContactForm_validRequest_recordsContact() throws Exception {
        String expectedEmail = "john@example.com";
        String expectedName = "John Doe";
        mockMvc.perform(post("/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "John Doe",
                                    "email": "john@example.com",
                                    "subject": "Group Trip Inquiry",
                                    "message": "I'd like to plan a trip for 10 people.",
                                    "turnstileToken": "test-token"
                                }
                                """))
                .andExpect(status().isOk());

        verify(contactService).touch(eq(expectedEmail), eq(ContactSource.CONTACT_FORM), eq(expectedName), isNull());
    }

    @Test
    void submitContactForm_invalidCaptcha_doesNotRecordContact() throws Exception {
        when(turnstileService.verifyToken(anyString())).thenReturn(false);

        mockMvc.perform(post("/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "John Doe",
                                    "email": "john@example.com",
                                    "subject": "Support",
                                    "message": "Help me",
                                    "turnstileToken": "bad"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(contactService);
    }
```

- [ ] **Step 2: Run to confirm the new test fails**

Run: `cd myhive-backend && ./gradlew test --tests '*ContactControllerTest'`
Expected: `submitContactForm_validRequest_recordsContact` fails with "Wanted but not invoked".

- [ ] **Step 3: Wire the controller**

Replace the body of `ContactController` with:

```java
@RestController
@RequestMapping("/contact")
@RequiredArgsConstructor
public class ContactController {

    private final EmailService emailService;
    private final TurnstileService turnstileService;
    private final ContactService contactService;

    @PostMapping
    public ResponseEntity<Map<String, String>> submitContactForm(@Valid @RequestBody ContactRequest request) {
        if (!turnstileService.verifyToken(request.getTurnstileToken())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Captcha verification failed"));
        }
        emailService.sendContactNotification(request);
        contactService.touch(request.getEmail(), ContactSource.CONTACT_FORM, request.getName(), null);
        return ResponseEntity.ok(Map.of("message", "Message sent successfully"));
    }
}
```

Add imports `com.myhive.backend.model.ContactSource` and `com.myhive.backend.service.ContactService`. The touch goes after the notification on purpose: the contact form is synchronous and fail-loud (see `project_async_email` memory) — a mail failure returns 5xx and the visitor retries, so the address is captured on the successful attempt.

- [ ] **Step 4: Run the test class**

Run: `cd myhive-backend && ./gradlew test --tests '*ContactControllerTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/controller/ContactController.java \
        myhive-backend/src/test/java/com/myhive/backend/controller/ContactControllerTest.java
git commit -m "feat(contacts): record contact-form senders"
```

---

### Task 5: Capture the Stripe payer email

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/PaymentService.java` (fields ~L54-62, constructor ~L64-77, share-paid branch ~L278-283)
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/PaymentServiceTest.java` (`setUp` ~L59-68, `handleStripeEvent_lastSharePaid_transitionsToPaid` ~L434)

**Interfaces:**
- Consumes: `ContactService.touch(String, ContactSource, String, String)`; `StripeWebhookEvent.payerEmail()`; `Booking.getLocale()`

- [ ] **Step 1: Extend the unit test**

In `PaymentServiceTest` add:

```java
import com.myhive.backend.model.ContactSource;
    @Mock private ContactService contactService;
```

Change the constructor call in `setUp()` to pass `contactService` as the new last argument:

```java
        paymentService = new PaymentService(bookingService, bookingRepository, shareRepository,
                processedEventRepository, voteSessionService, stripeGateway, stripeProperties, emailService,
                frontendUrlResolver, contactService);
```

Add a new test right after `handleStripeEvent_lastSharePaid_transitionsToPaid`, reusing its arrange block but asserting the touch (copy the whole arrange block — a reader may not have the neighbour in view):

```java
    @Test
    void handleStripeEvent_sharePaid_recordsPayerAsContact() {
        String expectedPayerEmail = "payer@example.com";
        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setTripId("TRV-3");
        booking.setUserEmail("init@test.com");
        booking.setLocale("de");
        booking.setStatus(BookingStatus.DEPOSIT_PAID);
        booking.setTotalAmount(new BigDecimal("100.00"));
        booking.setAmountPaid(new BigDecimal("30.00"));

        BookingPaymentShare full = new BookingPaymentShare();
        full.setId(UUID.randomUUID());
        full.setBooking(booking);
        full.setType(PaymentShareType.BALANCE_FULL);
        full.setAmount(new BigDecimal("70.00"));
        full.setPaid(false);

        when(processedEventRepository.existsById("evt_3")).thenReturn(false);
        when(stripeGateway.constructEvent("b", "s"))
                .thenReturn(paidEvent("evt_3", full.getId().toString(), 7000L));
        when(shareRepository.findById(full.getId())).thenReturn(Optional.of(full));
        when(shareRepository.findByBookingId(booking.getId())).thenReturn(java.util.List.of(full));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentService.handleStripeEvent("b", "s");

        verify(contactService).touch(expectedPayerEmail, ContactSource.PAYMENT, null, "de");
    }
```

The existing private helper `paidEvent(String eventId, String shareId, long cents)` (bottom of the class, ~L337) builds a `StripeWebhookEvent` whose payer email is hard-coded to `"payer@example.com"` — that is why `expectedPayerEmail` uses that exact value. Do not change the helper.

- [ ] **Step 2: Run to confirm it fails to compile**

Run: `cd myhive-backend && ./gradlew test --tests '*PaymentServiceTest'`
Expected: compilation error — constructor arity.

- [ ] **Step 3: Wire the service**

In `PaymentService` add the import `com.myhive.backend.model.ContactSource`, the field, and the constructor parameter/assignment:

```java
    private final ContactService contactService;

    public PaymentService(BookingService bookingService, BookingRepository bookingRepository,
            BookingPaymentShareRepository shareRepository, ProcessedStripeEventRepository processedEventRepository,
            VoteSessionService voteSessionService, StripeGateway stripeGateway, StripeProperties stripeProperties,
            EmailService emailService, FrontendUrlResolver frontendUrlResolver, ContactService contactService) {
        // ...existing assignments...
        this.contactService = contactService;
    }
```

In the share-paid branch, replace

```java
        share.setPayerEmail(event.payerEmail());
        shareRepository.save(share);

        Booking booking = share.getBooking();
```

with

```java
        share.setPayerEmail(event.payerEmail());
        shareRepository.save(share);

        Booking booking = share.getBooking();
        contactService.touch(event.payerEmail(), ContactSource.PAYMENT, null, booking.getLocale());
```

- [ ] **Step 4: Run the test class**

Run: `cd myhive-backend && ./gradlew test --tests '*PaymentServiceTest'`
Expected: PASS (all existing tests plus the new one; existing ones are unaffected because a Mockito mock `ContactService` accepts any call).

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/service/PaymentService.java \
        myhive-backend/src/test/java/com/myhive/backend/service/PaymentServiceTest.java
git commit -m "feat(contacts): record Stripe payer emails"
```

---

### Task 6: Shared CSV cell helpers + `ContactCsvExporter`

**Files:**
- Create: `myhive-backend/src/main/java/com/myhive/backend/util/CsvCells.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/activity/ActivityCsvExporter.java` (private `nullSafe`/`sanitize` ~L97-110)
- Create: `myhive-backend/src/main/java/com/myhive/backend/service/ContactCsvExporter.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/util/CsvCellsTest.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/ContactCsvExporterTest.java`

**Interfaces:**
- Produces: `CsvCells.nullSafe(String): String`, `CsvCells.sanitize(String): String` (formula-injection guard, expects non-null)
- Produces: `ContactCsvExporter.exportAll(): String` (UTF-8 BOM + header `email,name,locale,first_source,last_source,first_seen_at,last_seen_at,touch_count,unsubscribed`)
- Consumes: `ContactService.findAllForExport(): List<ContactDTO>`

- [ ] **Step 1: Write the failing tests**

`myhive-backend/src/test/java/com/myhive/backend/util/CsvCellsTest.java`:

```java
package com.myhive.backend.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CsvCellsTest {

    @Test
    void nullSafe_mapsNullToEmpty() {
        assertThat(CsvCells.nullSafe(null)).isEmpty();
        assertThat(CsvCells.nullSafe("x")).isEqualTo("x");
    }

    @Test
    void sanitize_prefixesFormulaTriggersWithApostrophe() {
        assertThat(CsvCells.sanitize("=1+1")).isEqualTo("'=1+1");
        assertThat(CsvCells.sanitize("+49 170")).isEqualTo("'+49 170");
        assertThat(CsvCells.sanitize("-x")).isEqualTo("'-x");
        assertThat(CsvCells.sanitize("@me")).isEqualTo("'@me");
        assertThat(CsvCells.sanitize("\tx")).isEqualTo("'\tx");
        assertThat(CsvCells.sanitize("plain")).isEqualTo("plain");
        assertThat(CsvCells.sanitize("")).isEmpty();
    }
}
```

`myhive-backend/src/test/java/com/myhive/backend/service/ContactCsvExporterTest.java`:

```java
package com.myhive.backend.service;

import com.myhive.backend.dto.ContactDTO;
import com.myhive.backend.model.ContactSource;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContactCsvExporterTest {

    @Test
    void exportAll_writesBomHeaderAndSanitizedRows() {
        String expectedEmail = "anna@example.com";
        ContactDTO dto = new ContactDTO(UUID.randomUUID(), expectedEmail, "=HYPERLINK(evil)", "de",
                ContactSource.VOTE, ContactSource.BOOKING,
                LocalDateTime.of(2026, 9, 1, 10, 0), LocalDateTime.of(2026, 9, 12, 8, 30), 3, true);
        ContactService contactService = mock(ContactService.class);
        when(contactService.findAllForExport()).thenReturn(List.of(dto));
        ContactCsvExporter exporter = new ContactCsvExporter(contactService);

        String csv = exporter.exportAll();

        String[] lines = csv.split("\r?\n");
        assertThat(lines[0]).startsWith("﻿");
        assertThat(lines[0].substring(1)).isEqualTo(
                "email,name,locale,first_source,last_source,first_seen_at,last_seen_at,touch_count,unsubscribed");
        assertThat(lines[1]).isEqualTo(
                expectedEmail + ",'=HYPERLINK(evil),de,VOTE,BOOKING,2026-09-01T10:00,2026-09-12T08:30,3,true");
    }
}
```

- [ ] **Step 2: Run to confirm both fail to compile**

Run: `cd myhive-backend && ./gradlew test --tests '*CsvCellsTest' --tests '*ContactCsvExporterTest'`
Expected: compilation errors — `CsvCells`, `ContactCsvExporter` do not exist.

- [ ] **Step 3: Create `CsvCells` and point `ActivityCsvExporter` at it**

`myhive-backend/src/main/java/com/myhive/backend/util/CsvCells.java`:

```java
package com.myhive.backend.util;

/** Cell-level helpers shared by the CSV exporters. */
public final class CsvCells {

    private CsvCells() {}

    public static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    /**
     * Formula-injection guard: a cell starting with = + - @ tab or CR would be executed by
     * Excel/Sheets; a leading apostrophe makes it literal text.
     */
    public static String sanitize(String s) {
        if (s.isEmpty()) {
            return s;
        }
        char c = s.charAt(0);
        if (c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r') {
            return "'" + s;
        }
        return s;
    }
}
```

In `ActivityCsvExporter`: delete the private `nullSafe` and `sanitize` methods, add `import com.myhive.backend.util.CsvCells;`, and replace every call `nullSafe(...)` → `CsvCells.nullSafe(...)` and `sanitize(...)` → `CsvCells.sanitize(...)` (they sit inside `toRow`). Run `./gradlew test --tests '*ActivityCsvExporterTest'` afterwards — behaviour must be identical.

- [ ] **Step 4: Create `ContactCsvExporter`**

`myhive-backend/src/main/java/com/myhive/backend/service/ContactCsvExporter.java`:

```java
package com.myhive.backend.service;

import com.myhive.backend.dto.ContactDTO;
import com.myhive.backend.util.CsvCells;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ContactCsvExporter {

    static final String[] HEADER = {
            "email", "name", "locale", "first_source", "last_source",
            "first_seen_at", "last_seen_at", "touch_count", "unsubscribed"
    };

    private static final String BOM = "﻿";

    private final ContactService contactService;

    public String exportAll() {
        StringWriter out = new StringWriter();
        out.write(BOM);
        try (CSVWriter writer = new CSVWriter(out,
                CSVWriter.DEFAULT_SEPARATOR,
                CSVWriter.DEFAULT_QUOTE_CHARACTER,
                CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                CSVWriter.DEFAULT_LINE_END)) {
            writer.writeNext(HEADER, false);
            for (ContactDTO contact : contactService.findAllForExport()) {
                writer.writeNext(toRow(contact), false);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write CSV", e);
        }
        return out.toString();
    }

    private static String[] toRow(ContactDTO c) {
        return new String[] {
                c.getEmail(),
                CsvCells.sanitize(CsvCells.nullSafe(c.getName())),
                CsvCells.nullSafe(c.getLocale()),
                c.getFirstSource().name(),
                c.getLastSource().name(),
                timestamp(c.getFirstSeenAt()),
                timestamp(c.getLastSeenAt()),
                String.valueOf(c.getTouchCount()),
                String.valueOf(c.isUnsubscribed())
        };
    }

    private static String timestamp(LocalDateTime value) {
        return value == null ? "" : value.toString();
    }
}
```

- [ ] **Step 5: Run the tests**

Run: `cd myhive-backend && ./gradlew test --tests '*CsvCellsTest' --tests '*ContactCsvExporterTest' --tests '*ActivityCsvExporterTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/util/CsvCells.java \
        myhive-backend/src/main/java/com/myhive/backend/service/activity/ActivityCsvExporter.java \
        myhive-backend/src/main/java/com/myhive/backend/service/ContactCsvExporter.java \
        myhive-backend/src/test/java/com/myhive/backend/util/CsvCellsTest.java \
        myhive-backend/src/test/java/com/myhive/backend/service/ContactCsvExporterTest.java
git commit -m "feat(contacts): CSV export with shared cell sanitizer"
```

---

### Task 7: Admin endpoints `GET /admin/contacts` and `GET /admin/contacts/export`

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/controller/AdminController.java` (fields ~L64-75; add endpoints after `/bookings/stats` ~L92)
- Modify: `myhive-backend/src/main/java/com/myhive/backend/config/SecurityConfig.java` (~L84, before the `/admin/**` catch-all)
- Test: `myhive-backend/src/test/java/com/myhive/backend/controller/AdminContactsControllerTest.java`

**Interfaces:**
- Consumes: `ContactService.search(String, Pageable)`, `ContactCsvExporter.exportAll()`, `JwtTestHelper.adminJwt()/managerJwt()`
- Produces: `GET /admin/contacts?q=&page=&size=` → Spring `Page<ContactDTO>` JSON (`content`, `totalPages`, `totalElements`); `GET /admin/contacts/export` → `text/csv;charset=UTF-8` attachment `contacts-YYYY-MM-DD.csv`

- [ ] **Step 1: Write the failing controller test**

`myhive-backend/src/test/java/com/myhive/backend/controller/AdminContactsControllerTest.java`:

```java
package com.myhive.backend.controller;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.model.ContactSource;
import com.myhive.backend.repository.ContactRepository;
import com.myhive.backend.service.ContactService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.myhive.backend.util.JwtTestHelper.adminJwt;
import static com.myhive.backend.util.JwtTestHelper.managerJwt;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Not @Transactional: contacts are written through ContactService.touch (REQUIRES_NEW).
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
class AdminContactsControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ContactService contactService;
    @Autowired private ContactRepository contactRepository;

    private final List<String> createdEmails = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (String email : createdEmails) {
            contactRepository.findByEmail(email).ifPresent(contactRepository::delete);
        }
    }

    private String seedContact(String marker) {
        String email = "c-" + marker + "-" + UUID.randomUUID() + "@example.com";
        createdEmails.add(email);
        contactService.touch(email, ContactSource.VOTE, null, null);
        return email;
    }

    @Test
    void listContacts_admin_filtersByQuery() throws Exception {
        String marker = UUID.randomUUID().toString().substring(0, 8);
        String expectedEmail = seedContact(marker);
        seedContact("other");

        mockMvc.perform(get("/admin/contacts").param("q", marker).with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].email", is(expectedEmail)))
                .andExpect(jsonPath("$.content[0].firstSource", is("VOTE")))
                .andExpect(jsonPath("$.content[0].unsubscribed", is(false)));
    }

    @Test
    void listContacts_manager_isForbidden() throws Exception {
        mockMvc.perform(get("/admin/contacts").with(managerJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void exportContacts_admin_returnsCsvAttachment() throws Exception {
        String expectedEmail = seedContact("csv");

        mockMvc.perform(get("/admin/contacts/export").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("contacts-")))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(containsString("email,name,locale,first_source")))
                .andExpect(content().string(containsString(expectedEmail)));
    }

    @Test
    void exportContacts_manager_isForbidden() throws Exception {
        mockMvc.perform(get("/admin/contacts/export").with(managerJwt()))
                .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 2: Run to confirm the admin tests fail with 404**

Run: `cd myhive-backend && ./gradlew test --tests '*AdminContactsControllerTest'`
Expected: the two admin tests fail (404); the two manager tests already pass via the `/admin/**` catch-all.

- [ ] **Step 3: Add the endpoints**

In `AdminController` add imports:

```java
import com.myhive.backend.dto.ContactDTO;
import com.myhive.backend.service.ContactCsvExporter;
import com.myhive.backend.service.ContactService;
```

Add fields next to the other services:

```java
    private final ContactService contactService;
    private final ContactCsvExporter contactCsvExporter;
```

Insert after `getBookingStats()`:

```java
    // ── Contacts (sales/ops address book) ──────────────────────────────────

    @GetMapping("/contacts")
    public ResponseEntity<Page<ContactDTO>> getContacts(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100), Sort.by("lastSeenAt").descending());
        return ResponseEntity.ok(contactService.search(q, pageRequest));
    }

    @GetMapping(value = "/contacts/export", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> exportContacts() {
        byte[] body = contactCsvExporter.exportAll().getBytes(StandardCharsets.UTF_8);
        String filename = "contacts-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }
```

- [ ] **Step 4: Make the security rule explicit**

In `SecurityConfig`, directly above the line `.requestMatchers("/admin/**").hasRole("ADMIN")` insert:

```java
                        // Contacts (customer PII): ADMIN only — deliberately not MANAGER
                        .requestMatchers("/admin/contacts/**").hasRole("ADMIN")
```

- [ ] **Step 5: Run the controller test and the whole admin suite**

Run: `cd myhive-backend && ./gradlew test --tests '*AdminContactsControllerTest' --tests '*AdminControllerIntegrationTest' --tests '*SecurityConfig*'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/controller/AdminController.java \
        myhive-backend/src/main/java/com/myhive/backend/config/SecurityConfig.java \
        myhive-backend/src/test/java/com/myhive/backend/controller/AdminContactsControllerTest.java
git commit -m "feat(contacts): admin list + CSV export endpoints (ADMIN only)"
```

---

### Task 8: Admin UI — `/admin/contacts` page

**Files:**
- Modify: `myhive-react-app/src/services/adminApi.js` (`exportActivitiesCsv` ~L130-150; add `getContactsPaged`, `exportContactsCsv`)
- Create: `myhive-react-app/src/pages/AdminContacts.js`
- Modify: `myhive-react-app/src/AdminApp.js` (route)
- Modify: `myhive-react-app/src/components/AdminLayout.js` (nav link, ~L34-36)
- Test: `myhive-react-app/src/pages/AdminContacts.test.js`

**Interfaces:**
- Consumes: `GET /admin/contacts?q=&page=&size=` → `{content, totalPages, totalElements}`; `GET /admin/contacts/export`
- Consumes (existing): `useAdminApi()`, `useAuthErrorHandler()`, `components/Pagination` (`page`, `totalPages`, `onPageChange`), `utils/format` `formatDateTime(dateStr)`
- Produces: `adminApi.getContactsPaged(page, size, q)`, `adminApi.exportContactsCsv()`

- [ ] **Step 1: Write the failing page test**

`myhive-react-app/src/pages/AdminContacts.test.js`:

```js
import {render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import AdminContacts from './AdminContacts';

const mockApi = {
    getContactsPaged: jest.fn(),
    exportContactsCsv: jest.fn(),
};

jest.mock('../hooks/useAdminApi', () => ({useAdminApi: () => mockApi}));
// Must return a stable reference — it is a dependency of the page's useCallback;
// a fresh identity per render causes an endless refetch loop.
jest.mock('../hooks/useAuthErrorHandler', () => {
    const stable = () => false;
    return {useAuthErrorHandler: () => stable};
});

const contact = {
    id: '1',
    email: 'anna@example.com',
    name: 'Anna Example',
    locale: 'de',
    firstSource: 'VOTE',
    lastSource: 'BOOKING',
    firstSeenAt: '2026-09-01T10:00:00',
    lastSeenAt: '2026-09-12T08:30:00',
    touchCount: 3,
    unsubscribed: true,
};

beforeEach(() => {
    // CRA's jest preset resets mocks before each test, so set implementations here.
    mockApi.getContactsPaged.mockResolvedValue({content: [contact], totalPages: 1, totalElements: 1});
    mockApi.exportContactsCsv.mockResolvedValue();
});

test('renders contacts with source, touches and the unsubscribed badge', async () => {
    render(<AdminContacts/>);

    expect(await screen.findByText('anna@example.com')).toBeInTheDocument();
    expect(screen.getByText('Anna Example')).toBeInTheDocument();
    expect(screen.getByText('VOTE → BOOKING')).toBeInTheDocument();
    expect(screen.getByText('Unsubscribed')).toBeInTheDocument();
    expect(mockApi.getContactsPaged).toHaveBeenCalledWith(0, 20, '');
});

test('typing a search term refetches with the query', async () => {
    const user = userEvent.setup();
    render(<AdminContacts/>);
    await screen.findByText('anna@example.com');

    await user.type(screen.getByPlaceholderText('Search email or name'), 'anna');

    await waitFor(() => expect(mockApi.getContactsPaged).toHaveBeenLastCalledWith(0, 20, 'anna'));
});

test('Export CSV calls the export API', async () => {
    const user = userEvent.setup();
    render(<AdminContacts/>);
    await screen.findByText('anna@example.com');

    await user.click(screen.getByRole('button', {name: 'Export CSV'}));

    expect(mockApi.exportContactsCsv).toHaveBeenCalledTimes(1);
});

test('shows the empty state when there are no contacts', async () => {
    mockApi.getContactsPaged.mockResolvedValue({content: [], totalPages: 0, totalElements: 0});
    render(<AdminContacts/>);

    expect(await screen.findByText('No contacts yet.')).toBeInTheDocument();
});
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `cd myhive-react-app && CI=true npm test -- --watchAll=false AdminContacts`
Expected: FAIL — `Cannot find module './AdminContacts'`.

- [ ] **Step 3: Extend `adminApi.js` with a shared download helper**

Inside `createAdminApi`, above the returned object, add:

```js
    async function downloadCsv(url, fallbackFilename, errorMessage) {
        const token = await getAccessToken();
        const response = await fetch(url, {
            headers: {Authorization: `Bearer ${token}`},
        });
        await handleError(response, errorMessage);
        const blob = await response.blob();
        const filename = parseContentDispositionFilename(response.headers.get('content-disposition'))
            || fallbackFilename;
        const objectUrl = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = objectUrl;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
        link.remove();
        URL.revokeObjectURL(objectUrl);
    }
```

Replace the body of `exportActivitiesCsv` with:

```js
        async exportActivitiesCsv(destinationId) {
            const url = destinationId
                ? `${API_BASE_URL}/admin/activities/export?destinationId=${encodeURIComponent(destinationId)}`
                : `${API_BASE_URL}/admin/activities/export`;
            await downloadCsv(url, `activities-${new Date().toISOString().slice(0, 10)}.csv`,
                'Failed to export activities');
        },
```

Add two methods next to it:

```js
        async getContactsPaged(page = 0, size = 20, q = '') {
            const params = new URLSearchParams({page, size});
            if (q) {
                params.append('q', q);
            }
            const response = await fetch(`${API_BASE_URL}/admin/contacts?${params}`, {
                headers: await authHeaders(),
            });
            await handleError(response, 'Failed to fetch contacts');
            return response.json();
        },

        async exportContactsCsv() {
            await downloadCsv(`${API_BASE_URL}/admin/contacts/export`,
                `contacts-${new Date().toISOString().slice(0, 10)}.csv`, 'Failed to export contacts');
        },
```

Run `CI=true npm test -- --watchAll=false adminApi AdminActivities` afterwards to confirm the activities export tests (if any) still pass.

- [ ] **Step 4: Create the page**

`myhive-react-app/src/pages/AdminContacts.js`:

```js
import {useCallback, useEffect, useState} from 'react';
import {Alert, Badge, Button, Card, Form, Spinner, Table} from 'react-bootstrap';
import {useAdminApi} from '../hooks/useAdminApi';
import {useAuthErrorHandler} from '../hooks/useAuthErrorHandler';
import Pagination from '../components/Pagination';
import {formatDateTime} from '../utils/format';

const PAGE_SIZE = 20;
const SEARCH_DEBOUNCE_MS = 300;

const COLUMNS = ['Email', 'Name', 'Locale', 'Sources', 'First seen', 'Last seen', 'Touches', 'Status'];

function AdminContacts() {
    const adminApi = useAdminApi();
    const handleAuthError = useAuthErrorHandler();
    const [contacts, setContacts] = useState([]);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [search, setSearch] = useState('');
    const [query, setQuery] = useState('');
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [exporting, setExporting] = useState(false);

    // Debounce keystrokes into the query that actually hits the API.
    useEffect(() => {
        const timer = setTimeout(() => {
            setQuery(search.trim());
            setPage(0);
        }, SEARCH_DEBOUNCE_MS);
        return () => clearTimeout(timer);
    }, [search]);

    const fetchContacts = useCallback(async () => {
        try {
            setLoading(true);
            setError('');
            const data = await adminApi.getContactsPaged(page, PAGE_SIZE, query);
            setContacts(data.content || []);
            setTotalPages(data.totalPages || 0);
            setTotalElements(data.totalElements || 0);
        } catch (err) {
            if (handleAuthError(err)) return;
            setError(err.message || 'Failed to load contacts');
        } finally {
            setLoading(false);
        }
    }, [adminApi, handleAuthError, page, query]);

    useEffect(() => {
        fetchContacts();
    }, [fetchContacts]);

    const handleExport = async () => {
        try {
            setExporting(true);
            setError('');
            await adminApi.exportContactsCsv();
        } catch (err) {
            if (handleAuthError(err)) return;
            setError(err.message || 'Failed to export contacts');
        } finally {
            setExporting(false);
        }
    };

    return (
        <>
            <div className="d-flex align-items-center justify-content-between mb-4">
                <div>
                    <h4 className="fw-bold mb-0">Contacts</h4>
                    <span className="text-muted small">{totalElements} addresses</span>
                </div>
                <Button variant="outline-primary" size="sm" onClick={handleExport} disabled={exporting}>
                    {exporting ? 'Exporting…' : 'Export CSV'}
                </Button>
            </div>

            <Form.Control
                type="search"
                className="mb-3"
                placeholder="Search email or name"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
            />

            {error && (
                <Alert variant="danger" className="d-flex align-items-center justify-content-between">
                    <span>{error}</span>
                    <Button variant="outline-danger" size="sm" onClick={fetchContacts}>Retry</Button>
                </Alert>
            )}

            <Card className="shadow-sm">
                <Card.Body className="p-0">
                    {loading ? (
                        <div className="d-flex justify-content-center py-5">
                            <Spinner animation="border" variant="primary"/>
                        </div>
                    ) : contacts.length === 0 ? (
                        <p className="text-muted text-center py-5">
                            {query ? 'No contacts match your search.' : 'No contacts yet.'}
                        </p>
                    ) : (
                        <>
                            <Table responsive hover className="mb-0 align-middle">
                                <thead>
                                <tr>
                                    {COLUMNS.map(label => (
                                        <th key={label} className="small text-muted text-uppercase">{label}</th>
                                    ))}
                                </tr>
                                </thead>
                                <tbody>
                                {contacts.map(contact => (
                                    <ContactRow key={contact.id} contact={contact}/>
                                ))}
                                </tbody>
                            </Table>
                            <Pagination page={page} totalPages={totalPages} onPageChange={setPage}/>
                        </>
                    )}
                </Card.Body>
            </Card>
        </>
    );
}

function ContactRow({contact}) {
    const sources = contact.firstSource === contact.lastSource
        ? contact.firstSource
        : `${contact.firstSource} → ${contact.lastSource}`;
    return (
        <tr>
            <td><a href={`mailto:${contact.email}`}>{contact.email}</a></td>
            <td>{contact.name || <span className="text-muted">—</span>}</td>
            <td>{(contact.locale || 'en').toUpperCase()}</td>
            <td className="small">{sources}</td>
            <td className="small text-nowrap">{formatDateTime(contact.firstSeenAt)}</td>
            <td className="small text-nowrap">{formatDateTime(contact.lastSeenAt)}</td>
            <td>{contact.touchCount}</td>
            <td>
                {contact.unsubscribed
                    ? <Badge bg="secondary">Unsubscribed</Badge>
                    : <Badge bg="success">OK to contact</Badge>}
            </td>
        </tr>
    );
}

export default AdminContacts;
```

- [ ] **Step 5: Register the route and nav link**

In `AdminApp.js` add `import AdminContacts from './pages/AdminContacts';` and, after the `blog` route:

```jsx
                    <Route path="contacts"
                           element={<ProtectedRoute requiredRole={['ADMIN']}><AdminContacts/></ProtectedRoute>}/>
```

In `AdminLayout.js`, after the Blog `Nav.Link`, add (ADMIN only, mirroring the backend rule):

```jsx
                        {user?.roles?.includes('ADMIN') && (
                            <Nav.Link as={NavLink} to="/admin/contacts">
                                Contacts
                            </Nav.Link>
                        )}
```

Check how `ProtectedRoute` reads `requiredRole` (it already accepts an array on the `bookings/:id` route) — no change needed there.

- [ ] **Step 6: Run the page test and the full CRA suite**

Run: `cd myhive-react-app && CI=true npm test -- --watchAll=false`
Expected: all green, including the 4 new `AdminContacts` tests.

- [ ] **Step 7: Verify in the browser**

Start backend (`cd myhive-backend && ./gradlew bootRun --args='--spring.profiles.active=dev'`) and Next (`cd myhive-next && npm run dev`), log in to `/admin` as ADMIN, open `/admin/contacts`: the page lists rows created by submitting the contact form / creating a vote in the same dev session, search narrows the list, Export CSV downloads a file that opens in Excel with a proper header. Log in as MANAGER: the nav link is absent and `/admin/contacts` redirects.

- [ ] **Step 8: Commit**

```bash
git add myhive-react-app/src/services/adminApi.js \
        myhive-react-app/src/pages/AdminContacts.js \
        myhive-react-app/src/pages/AdminContacts.test.js \
        myhive-react-app/src/AdminApp.js \
        myhive-react-app/src/components/AdminLayout.js
git commit -m "feat(admin): Contacts page with search, pagination and CSV export"
```

---

### Task 9: Docs, knowledge graph, final verification

**Files:**
- Modify: `README.md` (API Endpoints section ~L45-110)
- Modify: `CLAUDE.md` (Backend Structure table `entity/`, `service/`; Key Architectural Patterns)
- Modify: memory `project_overview.md` (after user approval, per `CLAUDE.md` rule 3)

- [ ] **Step 1: README**

In the API Endpoints section add, next to the admin bullets:

```markdown
- **Contacts (ADMIN only)**: `GET /admin/contacts?q=&page=&size=` (paged, searches email/name, newest activity first, `unsubscribed` flag from `email_suppressions`) and `GET /admin/contacts/export` (CSV, UTF-8 BOM, formula-injection safe). Rows are upserted by `ContactService.touch` from every email capture point — Trip Builder lead, vote creation, booking, contact form, Stripe payer — and are never auto-deleted. Prod table + backfill: Flyway `V5__contacts.sql`.
```

- [ ] **Step 2: CLAUDE.md**

Add `Contact` to the `entity/` row and `ContactService`/`ContactCsvExporter` to the `service/` row of the Backend Structure table. Add a Key Architectural Patterns bullet:

```markdown
- **Contacts (sales address book)**: `contacts` table, one row per normalized email, upserted by `ContactService.touch(email, source, name, locale)` in its own `REQUIRES_NEW` transaction (never throws — a contact write must not break the capturing flow). Sources: `TRIP_BUILDER` (`TripLeadService.create`), `VOTE` (`VoteSessionService.newSession`), `BOOKING` (both `BookingService` factories), `CONTACT_FORM` (`ContactController`), `PAYMENT` (Stripe webhook payer). Never auto-deleted (unlike `trip_leads`); `email_suppressions` remains the opt-out truth and is surfaced as `unsubscribed`. Admin: `/admin/contacts` page + `GET /admin/contacts{,/export}`, ADMIN only. Tests that go through `touch` must not be `@Transactional` and must clean up their own rows.
```

- [ ] **Step 3: Update the knowledge graph and run everything**

```bash
graphify update .
cd myhive-backend && ./gradlew build
cd ../myhive-react-app && CI=true npm test -- --watchAll=false
```

Expected: backend `BUILD SUCCESSFUL`, all Jest suites green.

- [ ] **Step 4: Commit**

```bash
git add README.md CLAUDE.md graphify-out
git commit -m "docs(contacts): document the contacts address book"
```

- [ ] **Step 5: Deployment notes for the user (not automated)**

1. Merge → Render deploys backend first; Flyway applies `V5` (table + backfill) before Hibernate starts. Check the deploy log for `Successfully applied 1 migration`.
2. Spot-check in prod: `SELECT count(*), min(first_seen_at) FROM contacts;` should roughly equal the number of distinct emails across `bookings`, `vote_sessions`, `trip_leads`, `booking_payment_shares`.
3. Privacy policy: the page currently describes trip reminders only. Keeping addresses indefinitely for sales follow-up is a retention statement worth a sentence there — the user's call, not part of this plan.
