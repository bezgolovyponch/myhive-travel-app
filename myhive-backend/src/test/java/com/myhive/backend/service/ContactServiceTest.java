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
import static org.assertj.core.api.Assertions.tuple;

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

    @Test
    void findAllForExport_flagsSuppressedAddresses() {
        String marker = UUID.randomUUID().toString().substring(0, 8);
        String expectedSuppressed = "c-" + marker + "-x@example.com";
        String expectedActive = "c-" + marker + "-y@example.com";
        createdEmails.add(expectedSuppressed);
        createdEmails.add(expectedActive);
        contactService.touch(expectedSuppressed, ContactSource.BOOKING, null, null);
        contactService.touch(expectedActive, ContactSource.BOOKING, null, null);
        EmailSuppression suppression = new EmailSuppression();
        suppression.setEmail(expectedSuppressed);
        emailSuppressionRepository.save(suppression);

        List<ContactDTO> all = contactService.findAllForExport();

        assertThat(all).filteredOn(dto -> dto.getEmail().contains(marker))
                .extracting(ContactDTO::getEmail, ContactDTO::isUnsubscribed)
                .containsExactlyInAnyOrder(tuple(expectedSuppressed, true), tuple(expectedActive, false));
    }
}
