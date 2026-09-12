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
import org.springframework.dao.DataIntegrityViolationException;
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
     * the stored value untouched. A concurrent-insert race on the unique email constraint is logged
     * at WARN with only the masked email — never the exception body, which embeds the raw address.
     */
    public void touch(String rawEmail, ContactSource source, String name, String locale) {
        String email = normalizeEmail(rawEmail);
        if (email == null) {
            return;
        }
        try {
            requiresNew.executeWithoutResult(status -> upsert(email, source, name, locale));
        } catch (DataIntegrityViolationException e) {
            // The other concurrent touch() won the insert race; its write already recorded this
            // address. Never log e.getMessage()/e here — both H2 and Postgres embed the raw email
            // in the constraint-violation detail.
            log.warn("Contact {} from {} lost a concurrent-insert race; the other write won",
                    EmailMasker.mask(email), source);
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
