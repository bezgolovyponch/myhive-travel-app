package com.myhive.backend.ai.service;

import com.myhive.backend.ai.exception.AiConflictException;
import com.myhive.backend.ai.exception.AiDisabledException;
import com.myhive.backend.ai.exception.AiLimitException;
import com.myhive.backend.ai.exception.AiNotFoundException;
import com.myhive.backend.ai.exception.LlmCallFailedException;
import com.myhive.backend.ai.exception.TurnstileFailedException;
import com.myhive.backend.ai.graph.JsonCodec;
import com.myhive.backend.ai.graph.PlannerGraph;
import com.myhive.backend.ai.graph.PlannerState;
import com.myhive.backend.ai.graph.ResumeReason;
import com.myhive.backend.ai.llm.AiProperties;
import com.myhive.backend.ai.llm.ChatMessage;
import com.myhive.backend.ai.llm.LlmUnavailableException;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.Tier;
import com.myhive.backend.ai.plan.ComposedPlan;
import com.myhive.backend.entity.AiGeneration;
import com.myhive.backend.entity.AiGenerationStatus;
import com.myhive.backend.entity.AiSession;
import com.myhive.backend.entity.AiSessionStatus;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.repository.AiGenerationRepository;
import com.myhive.backend.repository.AiSessionRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.service.TurnstileService;
import com.myhive.backend.util.Translations;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The planner's front door: it owns the {@code ai_sessions} row and drives the checkpointed graph
 * thread that carries the conversation.
 *
 * <p>None of the public methods that resume the graph are transactional. A chat turn blocks on a
 * model call for seconds and a generation for up to a minute, and holding a database connection
 * across that would exhaust the pool long before the planner ran out of capacity. The row updates
 * are single-statement saves instead, each in the repository's own transaction — which is also what
 * lets {@link PlanGenerationService#enqueue} commit its QUEUED row before the job thread reads it.
 * {@link SessionLocks} serialises everything per token, so there is no lost-update race to protect.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiSessionService {

    public static final int MAX_MESSAGES = 30;
    public static final int MAX_GENERATIONS = 5;

    private static final Map<String, String> GREETING = Map.of(
            "en", "Hey! I'm your stag-trip planner. How many days are you coming for, and how big is the group?",
            "de", "Hey! Ich bin dein Junggesellenabschied-Planer. Wie viele Tage kommt ihr, und wie groß ist die Gruppe?");
    private static final List<AiGenerationStatus> IN_FLIGHT =
            List.of(AiGenerationStatus.QUEUED, AiGenerationStatus.RUNNING);
    private static final String TIMEOUT_MARKER = "timed out";

    private final AiProperties props;
    private final PlannerGraph graph;
    private final AiSessionRepository sessionRepository;
    private final AiGenerationRepository generationRepository;
    private final DestinationRepository destinationRepository;
    private final PlanGenerationService generationService;
    private final TurnstileService turnstileService;
    private final SessionLocks locks;
    private final DailySessionCap dailyCap;
    private final ClientIpHasher ipHasher;

    /**
     * Everything an API response about one chat needs: the row, the graph state and where it is parked.
     *
     * <p>{@code latest} is the newest generation whatever its status, {@code latestReady} the newest one
     * that actually produced packages. They differ after a failed or rejected regeneration, and that is
     * the case the organizer has to be able to come back to: without the second one, a token restore
     * lands on a FAILED row and the packages a previous generation delivered are unreachable.
     *
     * <p>{@code firstTurnErrorCode} is set only by {@link #create}, when the inline first turn could not
     * reach the model; the session itself is fine and the same text can simply be re-sent.
     */
    public record SessionView(AiSession session, PlannerState state, String next, Optional<AiGeneration> latest,
                              Optional<AiGeneration> latestReady, String firstTurnErrorCode) {
    }

    /** One chat turn's result; {@code startedGeneration} is present when the turn completed the brief. */
    public record TurnOutcome(SessionView view, Optional<AiGeneration> startedGeneration) {
    }

    /**
     * A picked package, resolved to what the Trip Builder needs to fill a cart. {@code key} is the
     * authoritative pick: the generation row is stored by the graph's own sink transaction, so the
     * entity handed back here still carries its pre-selection snapshot.
     */
    public record Selection(AiGeneration generation, Tier key, int groupSize, List<UUID> activityIds) {
    }

    public SessionView create(String destinationSlug, String locale, String turnstileToken, String initialMessage,
            String clientIp) {
        requireEnabled();
        if (props.isTurnstileRequired() && (turnstileToken == null || !turnstileService.verifyToken(turnstileToken))) {
            throw new TurnstileFailedException();
        }
        // The slug is validated first: a typo must not cost the caller one of its twenty daily chats.
        Destination destination = destinationRepository.findBySlugWithCategories(destinationSlug)
                .orElseThrow(() -> new BadRequestException("Unknown destination: " + destinationSlug));
        String ipHash = ipHasher.hash(clientIp);
        dailyCap.check(ipHash);

        // Translations.normalize() answers null for English; the planner wants a real tag everywhere.
        String translationLocale = Translations.normalize(locale);
        String lc = translationLocale == null ? Translations.DEFAULT_LOCALE : translationLocale;
        AiSession session = newSession(destination, lc, ipHash);

        graph.seedParked(session.getToken(), seedFor(destination, lc, translationLocale));
        if (initialMessage == null || initialMessage.isBlank()) {
            return view(session);
        }
        try {
            return locks.withLock(session.getToken(), () -> turn(session, initialMessage)).view();
        } catch (LlmCallFailedException e) {
            // Not a 502: the row, the checkpoint thread and one of the caller's twenty daily chats are
            // already spent, and the documented retry ("re-send the same text") needs the token a
            // bodiless 502 would never hand out. The turn is answered as a created session carrying
            // firstTurnError instead - the user message is stored, so re-sending de-dupes.
            log.warn("AI planner first turn failed for session {}: {}", session.getToken(), e.getCode());
            return view(session, e.getCode());
        }
    }

    public SessionView get(UUID token) {
        requireEnabled();
        return view(find(token));
    }

    public TurnOutcome message(UUID token, String content) {
        requireEnabled();
        return locks.withLock(token, () -> turn(find(token), content));
    }

    /** Explicit "build it now", for the cases where the chat did not trigger a generation itself. */
    public AiGeneration requestGeneration(UUID token) {
        requireEnabled();
        return locks.withLock(token, () -> {
            AiSession session = find(token);
            Brief brief = graph.snapshot(token).state().brief();
            if (!brief.isReady()) {
                throw new AiConflictException("BRIEF_INCOMPLETE",
                        "Still missing: " + String.join(", ", brief.missingFields()));
            }
            requireNoGenerationInFlight(session, "A generation is already running");
            requireGenerationsLeft(session);
            // Before the park point is read: a dead run's checkpoint points into the generation branch,
            // and the walk below would then resume into it rather than towards awaitGeneration.
            generationService.ensureParked(session);
            if (!PlannerGraph.AWAIT_GENERATION.equals(graph.snapshot(token).next())) {
                // Moves awaitUser/awaitSelection to awaitGeneration and parks there; no model call,
                // because awaitGeneration is an interrupt point and only the job resumes past it.
                graph.update(token, Map.of(PlannerState.RESUME_REASON, ResumeReason.GENERATE.name()));
                brief = graph.runUntilInterrupt(token).state().brief();
            }
            touch(session);
            // Last, because it hands the graph thread to the pool.
            return startGeneration(session, brief);
        });
    }

    /**
     * Records which package the group picked and resolves it into activity ids. Transaction-free like
     * the rest: the select node makes no model call, but it does reach back into
     * {@link PlanGenerationService#selected} on its own transaction.
     */
    public Selection select(UUID generationId, Tier key) {
        requireEnabled();
        AiGeneration generation = generationRepository.findWithSessionById(generationId)
                .orElseThrow(() -> new AiNotFoundException("GENERATION_NOT_FOUND", "Unknown generation"));
        if (generation.getStatus() != AiGenerationStatus.READY) {
            throw new AiConflictException("GENERATION_NOT_READY", "Packages are not ready yet");
        }
        AiSession session = generation.getSession();
        return locks.withLock(session.getToken(), () -> {
            // A queued or running job owns this session's graph thread and its GENERATION_ID stamp.
            // Resuming with SELECT underneath it would stamp the wrong id and, while the thread is
            // parked at awaitGeneration, hand the job a thread that has already moved on.
            requireNoGenerationInFlight(session, "Your packages are being built, one moment");
            // Same reason as in turn(): the pick must not resume a generation that died mid-graph.
            generationService.ensureParked(session);
            ComposedPlan plan = JsonCodec.read(generation.getResult(), ComposedPlan.class);
            ComposedPlan.PackageResult chosen = plan.packages().stream()
                    .filter(p -> p.key() == key)
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("Package " + key + " is not in this generation"));
            // The Tier enum is validated by the time it gets here, so SELECTED_PACKAGE_KEY is never
            // a string the graph's Tier.valueOf could choke on.
            graph.update(session.getToken(), Map.of(
                    PlannerState.RESUME_REASON, ResumeReason.SELECT.name(),
                    PlannerState.SELECTED_PACKAGE_KEY, key.name(),
                    PlannerState.GENERATION_ID, generationId.toString()));
            graph.runUntilInterrupt(session.getToken());
            touch(session);
            Brief brief = JsonCodec.read(generation.getBriefSnapshot(), Brief.class);
            return new Selection(generation, key, brief.groupSize(), chosen.activityIds());
        });
    }

    /** Public because the controller guards reads of a generation with it, without loading a session. */
    public void requireEnabled() {
        if (!props.isEnabled()) {
            throw new AiDisabledException();
        }
    }

    /** One chat turn on a session the caller already holds the lock for. */
    private TurnOutcome turn(AiSession session, String content) {
        UUID token = session.getToken();
        if (session.getMessageCount() >= MAX_MESSAGES) {
            throw new AiLimitException("SESSION_TURN_LIMIT", "This chat reached its " + MAX_MESSAGES + "-message limit");
        }
        requireNoGenerationInFlight(session, "Your packages are being built, one moment");
        // Only now that no job owns the thread: a generation that died inside the graph left the
        // checkpoint pointing into the generation branch, and resuming it here would build a plan on
        // this request thread instead of answering the message.
        generationService.ensureParked(session);

        String text = content.strip();
        Map<String, Object> update = new HashMap<>();
        update.put(PlannerState.RESUME_REASON, ResumeReason.USER_MESSAGE.name());
        if (!isRepeatOfLastUserMessage(token, text)) {
            // PlannerState.message() stamps a fresh instant: the messages channel appends and would
            // drop a byte-identical map, so two turns must never produce the same entry.
            update.put(PlannerState.MESSAGES, List.of(PlannerState.message(ChatMessage.USER, text)));
        }
        graph.update(token, update);

        PlannerGraph.PlannerStateSnapshot snapshot;
        try {
            snapshot = graph.runUntilInterrupt(token);
        } catch (RuntimeException e) {
            throw new LlmCallFailedException(errorCodeOf(e), e);
        }
        // Persisted before the generation is started: hitting GENERATION_LIMIT must not make the turn free.
        session.setMessageCount(session.getMessageCount() + 1);
        touch(session);

        // The view is built while this thread is still the only one on the graph thread. Once the job
        // is submitted, snapshotting here would race the job's own update/resume on the same thread id.
        SessionView view = view(session);
        if (!PlannerGraph.AWAIT_GENERATION.equals(view.next())) {
            return new TurnOutcome(view, Optional.empty());
        }
        AiGeneration started = startGeneration(session, snapshot.state().brief());
        return new TurnOutcome(new SessionView(session, view.state(), view.next(), Optional.of(started),
                view.latestReady(), view.firstTurnErrorCode()), Optional.of(started));
    }

    private AiGeneration startGeneration(AiSession session, Brief brief) {
        requireGenerationsLeft(session);
        return generationService.enqueue(session, brief);
    }

    private void requireGenerationsLeft(AiSession session) {
        if (session.getGenerationCount() >= MAX_GENERATIONS) {
            throw new AiLimitException("GENERATION_LIMIT",
                    "This chat reached its " + MAX_GENERATIONS + "-generation limit");
        }
    }

    private void requireNoGenerationInFlight(AiSession session, String message) {
        if (generationRepository.existsBySessionIdAndStatusIn(session.getId(), IN_FLIGHT)) {
            throw new AiConflictException("GENERATION_IN_PROGRESS", message);
        }
    }

    /** Guards a double-submit: the same text twice in a row would be one appended map the channel drops. */
    private boolean isRepeatOfLastUserMessage(UUID token, String text) {
        List<ChatMessage> messages = graph.snapshot(token).state().messages();
        if (messages.isEmpty()) {
            return false;
        }
        ChatMessage last = messages.get(messages.size() - 1);
        return ChatMessage.USER.equals(last.role()) && last.content().equals(text);
    }

    private AiSession newSession(Destination destination, String lc, String ipHash) {
        AiSession session = new AiSession();
        session.setToken(UUID.randomUUID());
        session.setDestination(destination);
        session.setLocale(lc);
        session.setStatus(AiSessionStatus.COLLECTING);
        session.setClientIpHash(ipHash);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        session.setCreatedAt(now);
        session.setLastActivityAt(now);
        return sessionRepository.save(session);
    }

    /**
     * The opening state of a thread. The greeting is seeded as a stored assistant message rather than
     * generated, so opening the planner costs nothing and the first model call carries a real question.
     */
    private static Map<String, Object> seedFor(Destination destination, String lc, String translationLocale) {
        List<String> categorySlugs = destination.getCategories().stream()
                .map(Category::getSlug)
                .sorted()
                .toList();
        Map<String, Object> seed = new HashMap<>();
        seed.put(PlannerState.LOCALE, lc);
        seed.put(PlannerState.DESTINATION_ID, destination.getId().toString());
        seed.put(PlannerState.DESTINATION_NAME,
                Translations.pick(destination.getTranslations(), translationLocale, "name", destination.getName()));
        seed.put(PlannerState.CATEGORY_SLUGS, categorySlugs);
        seed.put(PlannerState.BRIEF, JsonCodec.write(Brief.empty()));
        seed.put(PlannerState.MESSAGES, List.of(PlannerState.message(ChatMessage.ASSISTANT,
                GREETING.getOrDefault(lc, GREETING.get(Translations.DEFAULT_LOCALE)))));
        return seed;
    }

    private void touch(AiSession session) {
        session.setLastActivityAt(LocalDateTime.now(ZoneOffset.UTC));
        sessionRepository.save(session);
    }

    private SessionView view(AiSession session) {
        return view(session, null);
    }

    private SessionView view(AiSession session, String firstTurnErrorCode) {
        PlannerGraph.PlannerStateSnapshot snapshot = graph.snapshot(session.getToken());
        return new SessionView(session, snapshot.state(), snapshot.next(),
                generationRepository.findFirstBySessionIdOrderByCreatedAtDesc(session.getId()),
                generationRepository.findFirstBySessionIdAndStatusOrderByCreatedAtDesc(session.getId(),
                        AiGenerationStatus.READY),
                firstTurnErrorCode);
    }

    private AiSession find(UUID token) {
        return sessionRepository.findByToken(token)
                .orElseThrow(() -> new AiNotFoundException("SESSION_NOT_FOUND", "Unknown or expired chat"));
    }

    /**
     * A timeout is worth telling the group apart from an outage ("try again" vs "we'll be back"). The
     * graph wraps node failures, so the whole chain is scanned rather than only its deepest cause.
     */
    static String errorCodeOf(Throwable failure) {
        for (Throwable t = failure; t != null && t != t.getCause(); t = t.getCause()) {
            if (t instanceof LlmUnavailableException && t.getMessage() != null
                    && t.getMessage().contains(TIMEOUT_MARKER)) {
                return "LLM_TIMEOUT";
            }
        }
        return "LLM_UNAVAILABLE";
    }
}
