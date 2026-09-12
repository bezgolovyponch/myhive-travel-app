# Contacts Daily Digest Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Once a day, email `booking@trivlu.com` a list of every contact captured since the previous digest, so sales sees new addresses without opening the admin console.

**Architecture:** A nullable `contacts.digest_sent_at` column is the watermark: a contact is "new" while it is null. `ContactDigestScheduler` (daily cron, 07:00 UTC) loads the un-stamped contacts through `ContactService.findUndigested()`, sends one synchronous email via `EmailService.sendNewContactsDigest(...)` to the existing `app.email.bookings-to` address, and only on success stamps those rows with `ContactService.markDigested(...)`. A failed send leaves the rows un-stamped so they go out with the next tick. Kill switch `app.contacts.digest-enabled` plus the usual `app.email.enabled` no-op. Prod runs Hibernate `ddl-auto=validate`, so the column ships as Flyway `V6`.

**Tech Stack:** Spring Boot 4.0 / Java 25 / JPA (H2 in dev+test, Postgres 18 prod) / Flyway (prod only) / Thymeleaf email templates / JUnit 5 + Mockito.

**Spec:** Agreed in chat on 2026-09-12 (builds on `docs/superpowers/plans/2026-09-12-contacts-capture.md`, branch `feat/contacts-capture`). Decisions: recipient is `app.email.bookings-to` (default `booking@trivlu.com`, env `EMAIL_BOOKINGS_TO`); watermark column rather than a 24 h window; the 28 backfilled contacts go out in the first digest (rows are not pre-stamped); no email when nothing is new.

## Global Constraints

- Google Java Style per `CLAUDE.md`: no wildcard imports, `@Override` everywhere, braces on every `if/else/for/while`, one variable per declaration, constants `UPPER_SNAKE_CASE`, static access via class name.
- Never log a raw email: `EmailMasker.mask(...)`; the digest's log/description lines carry counts only.
- Prod is `spring.jpa.hibernate.ddl-auto=validate`: every new column needs a Flyway migration whose type matches the entity (`timestamp(6)` for `LocalDateTime`).
- `EmailService.send` contract: `synchronous(true)` fails loudly (`EmailSendException`) when `app.email.enabled=false` or delivery fails — the scheduler relies on that to decide whether to stamp.
- Templates cannot use `#temporals` (no extras dependency): format dates in Java and pass strings.
- Test style: `expected`-prefixed variables for values shared between arrange and assert; `@Value` flags on components are set in tests with `ReflectionTestUtils.setField`.
- Tests that write contacts through `ContactService.touch` must NOT be `@Transactional`, must use unique emails and delete the rows they create (`ContactServiceTest` already has the `createdEmails` + `@AfterEach` pattern — reuse it).
- Backend commands: `cd myhive-backend && ./gradlew test --tests '<pattern>'`; full suite ~3 min. Commit after every task with the session's attribution trailers.

---

## File Structure

| File | Responsibility |
|------|----------------|
| `myhive-backend/src/main/resources/db/migration/V6__contacts_digest_sent_at.sql` | Adds the watermark column on prod |
| `myhive-backend/src/main/java/com/myhive/backend/entity/Contact.java` | + `digestSentAt` |
| `myhive-backend/src/main/java/com/myhive/backend/repository/ContactRepository.java` | + un-stamped query, bulk stamp |
| `myhive-backend/src/main/java/com/myhive/backend/service/ContactService.java` | + `findUndigested()`, `markDigested(...)` |
| `myhive-backend/src/main/java/com/myhive/backend/service/EmailService.java` | + `sendNewContactsDigest(...)` and its view row |
| `myhive-backend/src/main/resources/templates/email/contacts-digest.html` | The digest email |
| `myhive-backend/src/main/java/com/myhive/backend/service/ContactDigestScheduler.java` | Daily cron: load → send → stamp |
| `myhive-backend/src/main/resources/application.properties` | + `app.contacts.digest-enabled` |
| `README.md`, `CLAUDE.md` (gitignored, edit on disk only) | Docs |

---

### Task 1: Watermark column, repository queries, service methods

**Files:**
- Create: `myhive-backend/src/main/resources/db/migration/V6__contacts_digest_sent_at.sql`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/entity/Contact.java` (after `touchCount`)
- Modify: `myhive-backend/src/main/java/com/myhive/backend/repository/ContactRepository.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/ContactService.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/ContactServiceTest.java`

**Interfaces:**
- Consumes: `Contact`, `ContactRepository`, `ContactService.touch(...)`, `ContactDTO` (all on the branch).
- Produces: `Contact.getDigestSentAt()/setDigestSentAt(LocalDateTime)`; `ContactRepository.findByDigestSentAtIsNullOrderByFirstSeenAtAsc(): List<Contact>`; `ContactRepository.markDigested(Collection<UUID> ids, LocalDateTime sentAt): int`; `ContactService.findUndigested(): List<ContactDTO>` (oldest first, `unsubscribed` flag filled); `ContactService.markDigested(Collection<UUID> ids, LocalDateTime sentAt): void`.

- [ ] **Step 1: Write the failing tests**

Add to `ContactServiceTest` (imports needed: `java.time.LocalDateTime`, `java.time.ZoneOffset`, `java.util.List`, `com.myhive.backend.entity.Contact` already present):

```java
    @Test
    void findUndigested_returnsOnlyUnstampedContactsOldestFirst() {
        String marker = UUID.randomUUID().toString().substring(0, 8);
        String expectedOlder = "c-" + marker + "-older@example.com";
        String expectedNewer = "c-" + marker + "-newer@example.com";
        String stamped = "c-" + marker + "-stamped@example.com";
        createdEmails.add(expectedOlder);
        createdEmails.add(expectedNewer);
        createdEmails.add(stamped);
        contactService.touch(expectedOlder, ContactSource.VOTE, null, null);
        contactService.touch(expectedNewer, ContactSource.VOTE, null, null);
        contactService.touch(stamped, ContactSource.VOTE, null, null);
        Contact stampedRow = contactRepository.findByEmail(stamped).orElseThrow();
        stampedRow.setDigestSentAt(LocalDateTime.now(ZoneOffset.UTC));
        contactRepository.save(stampedRow);

        List<ContactDTO> fresh = contactService.findUndigested();

        assertThat(fresh).filteredOn(dto -> dto.getEmail().contains(marker))
                .extracting(ContactDTO::getEmail)
                .containsExactly(expectedOlder, expectedNewer);
    }

    @Test
    void markDigested_stampsOnlyTheGivenIds() {
        String marker = UUID.randomUUID().toString().substring(0, 8);
        String expectedStamped = "c-" + marker + "-a@example.com";
        String expectedUntouched = "c-" + marker + "-b@example.com";
        createdEmails.add(expectedStamped);
        createdEmails.add(expectedUntouched);
        contactService.touch(expectedStamped, ContactSource.BOOKING, null, null);
        contactService.touch(expectedUntouched, ContactSource.BOOKING, null, null);
        UUID stampedId = contactRepository.findByEmail(expectedStamped).orElseThrow().getId();
        LocalDateTime expectedSentAt = LocalDateTime.of(2026, 9, 13, 7, 0);

        contactService.markDigested(List.of(stampedId), expectedSentAt);

        assertThat(contactRepository.findByEmail(expectedStamped).orElseThrow().getDigestSentAt())
                .isEqualTo(expectedSentAt);
        assertThat(contactRepository.findByEmail(expectedUntouched).orElseThrow().getDigestSentAt()).isNull();
    }
```

- [ ] **Step 2: Run to confirm they fail to compile**

Run: `cd myhive-backend && ./gradlew test --tests '*ContactServiceTest'`
Expected: compilation errors — `setDigestSentAt`, `findUndigested`, `markDigested` missing.

- [ ] **Step 3: Entity + migration**

In `Contact.java`, after the `touchCount` field:

```java
    /** When this row was last included in the daily "new contacts" digest; null = not yet reported. */
    @Column(name = "digest_sent_at")
    private LocalDateTime digestSentAt;
```

`myhive-backend/src/main/resources/db/migration/V6__contacts_digest_sent_at.sql`:

```sql
-- Watermark for the daily new-contacts digest: null = not yet reported to sales.
-- Prod runs Hibernate ddl-auto=validate, so this column must exist before the entity does.
ALTER TABLE contacts ADD COLUMN IF NOT EXISTS digest_sent_at timestamp(6);
```

- [ ] **Step 4: Repository**

Replace the body of `ContactRepository` with:

```java
package com.myhive.backend.repository;

import com.myhive.backend.entity.Contact;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContactRepository extends JpaRepository<Contact, UUID> {

    Optional<Contact> findByEmail(String email);

    /** Both parameters receive the same search term; an empty term matches every row via the email clause. */
    Page<Contact> findByEmailContainingIgnoreCaseOrNameContainingIgnoreCase(
            String email, String name, Pageable pageable);

    /** Contacts the daily digest has not reported yet, oldest first. */
    List<Contact> findByDigestSentAtIsNullOrderByFirstSeenAtAsc();

    @Modifying
    @Query("update Contact c set c.digestSentAt = :sentAt where c.id in :ids")
    int markDigested(@Param("ids") Collection<UUID> ids, @Param("sentAt") LocalDateTime sentAt);
}
```

- [ ] **Step 5: Service**

In `ContactService`, add imports `java.util.Collection` and `java.util.UUID`, and these two methods after `findAllForExport`:

```java
    /** Contacts the daily digest has not reported yet, oldest first, with the opt-out flag filled in. */
    @Transactional(readOnly = true)
    public List<ContactDTO> findUndigested() {
        List<Contact> contacts = contactRepository.findByDigestSentAtIsNullOrderByFirstSeenAtAsc();
        Set<String> suppressed = allSuppressedEmails();
        return contacts.stream()
                .map(contact -> toDto(contact, suppressed.contains(contact.getEmail())))
                .toList();
    }

    /** Called only after the digest email was actually delivered — an unsent digest must not consume rows. */
    @Transactional
    public void markDigested(Collection<UUID> ids, LocalDateTime sentAt) {
        if (ids.isEmpty()) {
            return;
        }
        contactRepository.markDigested(ids, sentAt);
    }
```

(`@Transactional` here is the Spring annotation already imported in the class.)

- [ ] **Step 6: Run the tests, then the full suite**

Run: `cd myhive-backend && ./gradlew test --tests '*ContactServiceTest'` → PASS (8 tests). Then `./gradlew test` → all green (the new column must not break `SchemaColumnsTest` or the CSV exporter tests).

- [ ] **Step 7: Commit**

```bash
git add myhive-backend/src/main/resources/db/migration/V6__contacts_digest_sent_at.sql \
        myhive-backend/src/main/java/com/myhive/backend/entity/Contact.java \
        myhive-backend/src/main/java/com/myhive/backend/repository/ContactRepository.java \
        myhive-backend/src/main/java/com/myhive/backend/service/ContactService.java \
        myhive-backend/src/test/java/com/myhive/backend/service/ContactServiceTest.java
git commit -m "feat(contacts): digest watermark column and un-reported contact queries"
```

---

### Task 2: Digest email + template

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/EmailService.java` (view class near `VoteStandingView` ~L115; method after `sendContactNotification` ~L249)
- Create: `myhive-backend/src/main/resources/templates/email/contacts-digest.html`
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/EmailServiceTest.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/ContactsDigestTemplateRenderTest.java`

**Interfaces:**
- Consumes: `ContactDTO` getters, `EmailSpec.builder()` (private record in `EmailService`), `bookingsToEmail`, `maskEmail`.
- Produces: `public void sendNewContactsDigest(List<ContactDTO> contacts, String frontendUrl)` — synchronous, to `bookingsToEmail`, template `contacts-digest`, subject `"<N> new contact(s) on Trivlu — <yyyy-MM-dd UTC>"`; `EmailService.ContactDigestRow` (public final fields `email, name, source, firstSeen, locale, unsubscribed`).

- [ ] **Step 1: Write the failing tests**

Add to `EmailServiceTest` (imports: `com.myhive.backend.dto.ContactDTO`, `com.myhive.backend.model.ContactSource`, `java.time.LocalDateTime`, `java.util.List`, `java.util.UUID` if missing; `EmailSendException` from `com.myhive.backend.exception`):

```java
    @Test
    void sendNewContactsDigest_sendsSynchronouslyToBookingsAddress() {
        ContactDTO contact = new ContactDTO(UUID.randomUUID(), "anna@example.com", "Anna", "de",
                ContactSource.VOTE, ContactSource.BOOKING,
                LocalDateTime.of(2026, 9, 12, 8, 30), LocalDateTime.of(2026, 9, 12, 9, 0), 2, false);
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("contacts-digest"), any())).thenReturn("<html>ok</html>");

        emailService.sendNewContactsDigest(List.of(contact), "https://trivlu.com");

        // Synchronous on purpose: the scheduler stamps rows only after a confirmed delivery.
        verify(mailSender).send(mimeMessage);
        verifyNoInteractions(asyncMailSender);
    }

    @Test
    void sendNewContactsDigest_whenEmailDisabled_throwsSoRowsStayQueued() {
        ReflectionTestUtils.setField(emailService, "emailEnabled", false);
        ContactDTO contact = new ContactDTO(UUID.randomUUID(), "anna@example.com", null, null,
                ContactSource.VOTE, ContactSource.VOTE,
                LocalDateTime.of(2026, 9, 12, 8, 30), LocalDateTime.of(2026, 9, 12, 8, 30), 1, false);

        assertThatThrownBy(() -> emailService.sendNewContactsDigest(List.of(contact), "https://trivlu.com"))
                .isInstanceOf(EmailSendException.class);
        verifyNoInteractions(mailSender);
    }
```

`myhive-backend/src/test/java/com/myhive/backend/service/ContactsDigestTemplateRenderTest.java`:

```java
package com.myhive.backend.service;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class ContactsDigestTemplateRenderTest {

    @Test
    void rendersEveryRowAndTheAdminLink() {
        String expectedAdminUrl = "https://trivlu.com/admin/contacts";
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("count", 2);
        context.setVariable("digestDate", "2026-09-13");
        context.setVariable("adminUrl", expectedAdminUrl);
        context.setVariable("contacts", List.of(
                new EmailService.ContactDigestRow("anna@example.com", "Anna Example", "VOTE → BOOKING",
                        "2026-09-12 08:30 UTC", "DE", false),
                new EmailService.ContactDigestRow("bob@example.com", "", "CONTACT_FORM",
                        "2026-09-12 19:05 UTC", "EN", true)));

        String html = EmailTemplateTestSupport.engine().process("contacts-digest", context);

        assertThat(html)
                .contains("2 new contacts")
                .contains("2026-09-13")
                .contains("anna@example.com")
                .contains("Anna Example")
                .contains("VOTE → BOOKING")
                .contains("2026-09-12 08:30 UTC")
                .contains("bob@example.com")
                .contains("CONTACT_FORM")
                .contains("Unsubscribed")
                .contains(expectedAdminUrl)
                .doesNotContain("??");
        assertThat(html.indexOf("anna@example.com")).isLessThan(html.indexOf("bob@example.com"));
    }
}
```

- [ ] **Step 2: Run to confirm they fail**

Run: `cd myhive-backend && ./gradlew test --tests '*EmailServiceTest' --tests '*ContactsDigestTemplateRenderTest'`
Expected: compilation errors — `sendNewContactsDigest`, `ContactDigestRow` missing.

- [ ] **Step 3: View row + method in `EmailService`**

Add imports `com.myhive.backend.dto.ContactDTO`, `java.time.LocalDate`, `java.time.LocalDateTime`, `java.time.ZoneOffset`, `java.time.format.DateTimeFormatter` (some may exist). Add next to `VoteStandingView`:

```java
    /** One line of the daily new-contacts digest; dates are pre-formatted because the templates have no #temporals. */
    public static class ContactDigestRow {
        public final String email;
        public final String name;
        public final String source;
        public final String firstSeen;
        public final String locale;
        public final boolean unsubscribed;

        public ContactDigestRow(String email, String name, String source, String firstSeen,
                                String locale, boolean unsubscribed) {
            this.email = email;
            this.name = name;
            this.source = source;
            this.firstSeen = firstSeen;
            this.locale = locale;
            this.unsubscribed = unsubscribed;
        }
    }

    private static final DateTimeFormatter DIGEST_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'");
```

(Put the constant with the other `private static final` fields at the top of the class, not inside the nested class.)

Add after `sendContactNotification`:

```java
    /**
     * Daily "new contacts" digest for sales. Synchronous by design: the scheduler stamps the rows as
     * reported only when this returns normally, so a failed delivery re-queues them for tomorrow.
     */
    public void sendNewContactsDigest(List<ContactDTO> contacts, String frontendUrl) {
        String digestDate = LocalDate.now(ZoneOffset.UTC).toString();
        List<ContactDigestRow> rows = contacts.stream().map(EmailService::toDigestRow).toList();

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("count", rows.size());
        variables.put("digestDate", digestDate);
        variables.put("adminUrl", frontendUrl + "/admin/contacts");
        variables.put("contacts", rows);

        String noun = rows.size() == 1 ? " new contact" : " new contacts";
        send(EmailSpec.builder()
                .to(bookingsToEmail)
                .subject(rows.size() + noun + " on Trivlu — " + digestDate)
                .template("contacts-digest")
                .variables(variables)
                // Counts only — the addresses themselves must not reach the logs.
                .description("new contacts digest (" + rows.size() + " contacts)")
                .synchronous(true)
                .build());
    }

    private static ContactDigestRow toDigestRow(ContactDTO c) {
        String source = c.getFirstSource() == c.getLastSource()
                ? c.getFirstSource().name()
                : c.getFirstSource().name() + " → " + c.getLastSource().name();
        String locale = c.getLocale() == null ? "EN" : c.getLocale().toUpperCase(Locale.ROOT);
        return new ContactDigestRow(c.getEmail(), c.getName() == null ? "" : c.getName(), source,
                DIGEST_TIMESTAMP.format(c.getFirstSeenAt()), locale, c.isUnsubscribed());
    }
```

- [ ] **Step 4: Template**

`myhive-backend/src/main/resources/templates/email/contacts-digest.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title>New contacts on Trivlu</title>
    <style>
        body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
        .container { max-width: 680px; margin: 0 auto; background: white; }
        .header { background: #6A1B9A; color: white; padding: 30px; text-align: center; }
        .content { padding: 30px; }
        table { width: 100%; border-collapse: collapse; font-size: 14px; }
        th { text-align: left; font-size: 12px; text-transform: uppercase; color: #555; padding: 8px; border-bottom: 2px solid #e0e0e0; }
        td { padding: 8px; border-bottom: 1px solid #eee; vertical-align: top; }
        .muted { color: #888; }
        .badge { display: inline-block; padding: 2px 8px; border-radius: 10px; font-size: 12px; background: #6c757d; color: white; }
        .cta { display: inline-block; margin-top: 24px; padding: 12px 20px; background: #6A1B9A; color: white !important; text-decoration: none; border-radius: 4px; }
        .footer { background: #f8f9fa; padding: 20px; text-align: center; color: #666; font-size: 13px; }
    </style>
</head>
<body>
<div class="container">
    <div class="header">
        <img src="https://trivlu.com/logo-white.png" alt="Trivlu Travel" style="max-height: 60px; margin-bottom: 16px;">
        <h1><span th:text="${count}">2</span> new contacts</h1>
        <p>Addresses captured on Trivlu since the last digest · <span th:text="${digestDate}">2026-09-13</span></p>
    </div>
    <div class="content">
        <table>
            <thead>
            <tr>
                <th>Email</th>
                <th>Name</th>
                <th>Source</th>
                <th>First seen</th>
                <th>Lang</th>
            </tr>
            </thead>
            <tbody>
            <tr th:each="c : ${contacts}">
                <td>
                    <a th:href="'mailto:' + ${c.email}" th:text="${c.email}">anna@example.com</a>
                    <span class="badge" th:if="${c.unsubscribed}">Unsubscribed</span>
                </td>
                <td><span th:text="${c.name}" th:classappend="${c.name == ''} ? 'muted'">Anna Example</span></td>
                <td th:text="${c.source}">VOTE</td>
                <td th:text="${c.firstSeen}">2026-09-12 08:30 UTC</td>
                <td th:text="${c.locale}">DE</td>
            </tr>
            </tbody>
        </table>
        <a class="cta" th:href="${adminUrl}">Open the contacts list</a>
    </div>
    <div class="footer">
        <p>Contacts marked "Unsubscribed" opted out of automated emails — reach out manually only if there is an active request.</p>
    </div>
</div>
</body>
</html>
```

- [ ] **Step 5: Run the tests**

Run: `cd myhive-backend && ./gradlew test --tests '*EmailServiceTest' --tests '*ContactsDigestTemplateRenderTest'` → PASS.

- [ ] **Step 6: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/service/EmailService.java \
        myhive-backend/src/main/resources/templates/email/contacts-digest.html \
        myhive-backend/src/test/java/com/myhive/backend/service/EmailServiceTest.java \
        myhive-backend/src/test/java/com/myhive/backend/service/ContactsDigestTemplateRenderTest.java
git commit -m "feat(contacts): daily new-contacts digest email"
```

---

### Task 3: Scheduler, config, docs

**Files:**
- Create: `myhive-backend/src/main/java/com/myhive/backend/service/ContactDigestScheduler.java`
- Modify: `myhive-backend/src/main/resources/application.properties` (after `app.email.enabled`)
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/ContactDigestSchedulerTest.java`
- Modify: `README.md` (after the Contacts bullet in API Endpoints), `CLAUDE.md` (gitignored; extend the Contacts bullet on disk only)

**Interfaces:**
- Consumes: `ContactService.findUndigested()`, `ContactService.markDigested(Collection<UUID>, LocalDateTime)`, `EmailService.sendNewContactsDigest(List<ContactDTO>, String)`.
- Produces: `ContactDigestScheduler.sendDailyDigest()` on cron `0 0 7 * * *`; property `app.contacts.digest-enabled` (env `CONTACTS_DIGEST_ENABLED`, default `true`).

- [ ] **Step 1: Write the failing test**

`myhive-backend/src/test/java/com/myhive/backend/service/ContactDigestSchedulerTest.java`:

```java
package com.myhive.backend.service;

import com.myhive.backend.dto.ContactDTO;
import com.myhive.backend.exception.EmailSendException;
import com.myhive.backend.model.ContactSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContactDigestSchedulerTest {

    private static final String FRONTEND_URL = "https://trivlu.com";

    @Mock private ContactService contactService;
    @Mock private EmailService emailService;

    @InjectMocks
    private ContactDigestScheduler scheduler;

    @BeforeEach
    void enableFlags() {
        ReflectionTestUtils.setField(scheduler, "digestEnabled", true);
        ReflectionTestUtils.setField(scheduler, "emailEnabled", true);
        ReflectionTestUtils.setField(scheduler, "frontendUrl", FRONTEND_URL);
    }

    private static ContactDTO contact() {
        return new ContactDTO(UUID.randomUUID(), "anna@example.com", null, null,
                ContactSource.VOTE, ContactSource.VOTE,
                LocalDateTime.of(2026, 9, 12, 8, 30), LocalDateTime.of(2026, 9, 12, 8, 30), 1, false);
    }

    @Test
    void sendDailyDigest_sendsAndStampsWhenContactsAreNew() {
        ContactDTO expectedContact = contact();
        when(contactService.findUndigested()).thenReturn(List.of(expectedContact));

        scheduler.sendDailyDigest();

        verify(emailService).sendNewContactsDigest(List.of(expectedContact), FRONTEND_URL);
        verify(contactService).markDigested(eq(List.of(expectedContact.getId())), any(LocalDateTime.class));
    }

    @Test
    void sendDailyDigest_noContacts_sendsNothing() {
        when(contactService.findUndigested()).thenReturn(List.of());

        scheduler.sendDailyDigest();

        verifyNoInteractions(emailService);
        verify(contactService, never()).markDigested(anyCollection(), any());
    }

    @Test
    void sendDailyDigest_sendFails_leavesRowsUnstamped() {
        when(contactService.findUndigested()).thenReturn(List.of(contact()));
        doThrow(new EmailSendException("smtp down", null))
                .when(emailService).sendNewContactsDigest(any(), eq(FRONTEND_URL));

        scheduler.sendDailyDigest();

        verify(contactService, never()).markDigested(anyCollection(), any());
    }

    @Test
    void sendDailyDigest_noopWhenDigestDisabled() {
        ReflectionTestUtils.setField(scheduler, "digestEnabled", false);

        scheduler.sendDailyDigest();

        verifyNoInteractions(contactService, emailService);
    }

    @Test
    void sendDailyDigest_noopWhenEmailDisabled() {
        ReflectionTestUtils.setField(scheduler, "emailEnabled", false);

        scheduler.sendDailyDigest();

        verifyNoInteractions(contactService, emailService);
    }
}
```

- [ ] **Step 2: Run to confirm it fails to compile**

Run: `cd myhive-backend && ./gradlew test --tests '*ContactDigestSchedulerTest'`
Expected: compilation error — `ContactDigestScheduler` missing.

- [ ] **Step 3: Scheduler**

`myhive-backend/src/main/java/com/myhive/backend/service/ContactDigestScheduler.java`:

```java
package com.myhive.backend.service;

import com.myhive.backend.dto.ContactDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Once a day, mails sales every contact captured since the previous digest. Rows are stamped as
 * reported only after the email was delivered, so a failed send simply retries tomorrow.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContactDigestScheduler {

    private final ContactService contactService;
    private final EmailService emailService;

    /** Kill switch — capture keeps working when off, only the digest stops. */
    @Value("${app.contacts.digest-enabled:true}")
    private boolean digestEnabled;

    /** With the mailer off, the synchronous send would throw every day — skip instead. */
    @Value("${app.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${app.frontend.url:https://trivlu.com}")
    private String frontendUrl;

    /** 07:00 UTC daily — start of the working day in Berlin, before the inbox fills up. */
    @Scheduled(cron = "0 0 7 * * *")
    public void sendDailyDigest() {
        if (!digestEnabled || !emailEnabled) {
            return;
        }
        List<ContactDTO> fresh = contactService.findUndigested();
        if (fresh.isEmpty()) {
            log.info("Contacts digest: nothing new since the last run");
            return;
        }
        try {
            emailService.sendNewContactsDigest(fresh, frontendUrl);
        } catch (Exception e) {
            // Rows stay un-stamped on purpose: they go out with the next successful digest.
            log.error("Contacts digest failed; {} contacts stay queued: {}", fresh.size(), e.getMessage(), e);
            return;
        }
        List<UUID> ids = fresh.stream().map(ContactDTO::getId).toList();
        contactService.markDigested(ids, LocalDateTime.now(ZoneOffset.UTC));
        log.info("Contacts digest sent with {} contacts", ids.size());
    }
}
```

- [ ] **Step 4: Config**

In `myhive-backend/src/main/resources/application.properties`, directly after the `app.email.enabled=...` line:

```properties
# Daily "new contacts" digest to app.email.bookings-to (07:00 UTC). Kill switch; capture keeps running when off.
app.contacts.digest-enabled=${CONTACTS_DIGEST_ENABLED:true}
```

- [ ] **Step 5: Run the test, then the full suite**

Run: `cd myhive-backend && ./gradlew test --tests '*ContactDigestSchedulerTest'` → PASS (5 tests). Then `./gradlew build` → BUILD SUCCESSFUL.

- [ ] **Step 6: Docs**

README.md — after the "Contacts (ADMIN only)" bullet add:

```markdown
- **Contacts digest**: `ContactDigestScheduler` mails `app.email.bookings-to` (default `booking@trivlu.com`) once a day at 07:00 UTC with every contact whose `digest_sent_at` is still null, then stamps those rows; a failed send leaves them queued for the next run. Kill switch `CONTACTS_DIGEST_ENABLED` (default `true`); no-op when email is disabled. Prod column: Flyway `V6__contacts_digest_sent_at.sql`.
```

CLAUDE.md (on disk only — it is gitignored) — append to the end of the "Contacts (sales address book)" bullet:

```markdown
 Daily digest: `ContactDigestScheduler` (07:00 UTC) mails `app.email.bookings-to` the rows with `digest_sent_at IS NULL` and stamps them only after a synchronous send succeeds; kill switch `app.contacts.digest-enabled` (`CONTACTS_DIGEST_ENABLED`).
```

- [ ] **Step 7: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/service/ContactDigestScheduler.java \
        myhive-backend/src/main/resources/application.properties \
        myhive-backend/src/test/java/com/myhive/backend/service/ContactDigestSchedulerTest.java \
        README.md
git commit -m "feat(contacts): daily digest scheduler with kill switch"
```
