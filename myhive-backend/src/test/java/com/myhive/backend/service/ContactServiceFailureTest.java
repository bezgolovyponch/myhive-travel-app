package com.myhive.backend.service;

import com.myhive.backend.model.ContactSource;
import com.myhive.backend.repository.ContactRepository;
import com.myhive.backend.repository.EmailSuppressionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
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

    @Test
    void touch_duplicateInsertRace_logsMaskedEmailOnly(CapturedOutput output) {
        String rawEmail = "anna@example.com";
        String expectedMasked = "a***@example.com";
        when(contactRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(contactRepository.save(any())).thenThrow(new DataIntegrityViolationException(
                "duplicate key value violates unique constraint uk_contacts_email "
                        + "Detail: Key (email)=(anna@example.com) already exists."));
        ContactService service = new ContactService(contactRepository, emailSuppressionRepository, transactionManager);

        assertThatCode(() -> service.touch(rawEmail, ContactSource.BOOKING, "Anna", null))
                .doesNotThrowAnyException();

        assertThat(output.getOut() + output.getErr()).doesNotContain(rawEmail);
        assertThat(output.getOut() + output.getErr()).contains(expectedMasked);
    }
}
