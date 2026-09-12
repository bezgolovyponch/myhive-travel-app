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
