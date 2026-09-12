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
