package com.myhive.backend.ai.controller;

import com.myhive.backend.ai.dto.AiDtoMapper;
import com.myhive.backend.ai.dto.CreateSessionRequest;
import com.myhive.backend.ai.dto.GenerationDTO;
import com.myhive.backend.ai.dto.SelectPackageRequest;
import com.myhive.backend.ai.dto.SelectionResponseDTO;
import com.myhive.backend.ai.dto.SendMessageRequest;
import com.myhive.backend.ai.dto.SessionStateDTO;
import com.myhive.backend.ai.dto.TurnResponseDTO;
import com.myhive.backend.ai.exception.AiNotFoundException;
import com.myhive.backend.ai.service.AiSessionService;
import com.myhive.backend.entity.AiGeneration;
import com.myhive.backend.repository.AiGenerationRepository;
import com.myhive.backend.util.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The public planner API of {@code docs/api/ai-planner-api.md}. No auth: the session token in the
 * path is the credential, so nothing here is logged beyond what the service layer already records —
 * never a message body, a brief or a prompt.
 */
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiPlannerController {

    private static final String UNKNOWN_CLIENT_IP = "unknown";

    private final AiSessionService sessionService;
    private final AiGenerationRepository generationRepository;
    private final AiDtoMapper mapper;

    @PostMapping("/sessions")
    public ResponseEntity<SessionStateDTO> create(@Valid @RequestBody CreateSessionRequest request,
            HttpServletRequest httpRequest) {
        AiSessionService.SessionView view = sessionService.create(request.destinationSlug(), request.locale(),
                request.turnstileToken(), request.initialMessage(), clientIp(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.sessionState(view));
    }

    @GetMapping("/sessions/{token}")
    public SessionStateDTO get(@PathVariable UUID token) {
        return mapper.sessionState(sessionService.get(token));
    }

    @PostMapping("/sessions/{token}/messages")
    public TurnResponseDTO message(@PathVariable UUID token, @Valid @RequestBody SendMessageRequest request) {
        return mapper.turn(sessionService.message(token, request.content()));
    }

    @PostMapping("/sessions/{token}/generations")
    public ResponseEntity<GenerationDTO> generate(@PathVariable UUID token) {
        AiGeneration generation = sessionService.requestGeneration(token);
        return ResponseEntity.accepted().body(mapper.generation(generation));
    }

    @GetMapping("/generations/{id}")
    public GenerationDTO generation(@PathVariable UUID id) {
        // The only endpoint that never loads a session, so the kill switch has to be asked here.
        sessionService.requireEnabled();
        return mapper.generation(withSession(id));
    }

    @PostMapping("/generations/{id}/select")
    public SelectionResponseDTO select(@PathVariable UUID id, @Valid @RequestBody SelectPackageRequest request) {
        AiSessionService.Selection selection = sessionService.select(id, request.packageKey());
        // selection.key(), not the entity: the graph's own transaction stored the pick, so the row
        // handed back here still carries its pre-selection snapshot.
        return new SelectionResponseDTO(selection.key().name(), selection.groupSize(),
                mapper.tripItems(selection.activityIds(), selection.generation().getSession().getLocale()));
    }

    /**
     * The session comes along: mapping a generation needs its token, and this runs outside any
     * transaction, where a lazy proxy would blow up.
     */
    private AiGeneration withSession(UUID id) {
        return generationRepository.findWithSessionById(id)
                .orElseThrow(() -> new AiNotFoundException("GENERATION_NOT_FOUND", "Unknown generation"));
    }

    /**
     * The same caller {@code RateLimitFilter} sees, resolved by the same rule: the daily session cap
     * has to key on an address a client cannot pick for itself, or twenty chats per network becomes
     * twenty chats per forged header.
     */
    static String clientIp(HttpServletRequest request) {
        String resolved = ClientIp.resolve(request);
        // A blank address still has to hash to a stable bucket: the daily cap must count these calls
        // as coming from somewhere rather than letting an address-less caller start chats forever.
        return resolved == null || resolved.isBlank() ? UNKNOWN_CLIENT_IP : resolved;
    }
}
