package com.myhive.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.exception.BadRequestException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.myhive.backend.repository.VoteSessionRepository;

@ExtendWith(MockitoExtension.class)
class VoteSessionServiceManagerTest {

    @Mock
    private VoteSessionRepository voteSessionRepository;

    @InjectMocks
    private VoteSessionService voteSessionService;

    @Test
    void requireManager_returnsSession_whenTokenMatches() {
        UUID shareToken = UUID.randomUUID();
        UUID managerToken = UUID.randomUUID();
        VoteSession session = new VoteSession();
        session.setId(UUID.randomUUID());
        session.setManagerToken(managerToken);
        when(voteSessionRepository.findByShareToken(shareToken)).thenReturn(Optional.of(session));

        VoteSession result = voteSessionService.requireManager(shareToken, managerToken);

        assertThat(result.getManagerToken()).isEqualTo(managerToken);
    }

    @Test
    void requireManager_throws_whenTokenWrong() {
        UUID shareToken = UUID.randomUUID();
        VoteSession session = new VoteSession();
        session.setManagerToken(UUID.randomUUID());
        when(voteSessionRepository.findByShareToken(shareToken)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> voteSessionService.requireManager(shareToken, UUID.randomUUID()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void requireManagerById_returnsSession_whenTokenMatches() {
        UUID sessionId = UUID.randomUUID();
        UUID managerToken = UUID.randomUUID();
        VoteSession session = new VoteSession();
        session.setId(sessionId);
        session.setManagerToken(managerToken);
        when(voteSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        VoteSession result = voteSessionService.requireManagerById(sessionId, managerToken);

        assertThat(result.getId()).isEqualTo(sessionId);
    }
}
