package com.myhive.backend.ai.llm;

import com.myhive.backend.ai.plan.PlanDraft;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Scripted gateway: tests queue answers in call order and inspect the requests afterwards. */
public class FakeLlmGateway implements LlmGateway {

    private final Deque<ChatTurnResult> chatAnswers = new ArrayDeque<>();
    private final Deque<PlanDraft> planAnswers = new ArrayDeque<>();
    private final Deque<PlanDraft> repairAnswers = new ArrayDeque<>();
    private final Deque<TextRefreshResult> refreshAnswers = new ArrayDeque<>();
    private RuntimeException nextChatFailure;
    private RuntimeException nextPlanFailure;
    private RuntimeException nextRefreshFailure;
    public final List<ChatTurnRequest> chatRequests = new ArrayList<>();
    public final List<PlanRequest> planRequests = new ArrayList<>();
    public final List<RepairRequest> repairRequests = new ArrayList<>();
    public final List<TextRefreshRequest> refreshRequests = new ArrayList<>();

    public FakeLlmGateway queueChat(ChatTurnResult... results) {
        chatAnswers.addAll(List.of(results));
        return this;
    }

    public FakeLlmGateway queuePlan(PlanDraft... drafts) {
        planAnswers.addAll(List.of(drafts));
        return this;
    }

    public FakeLlmGateway queueRepair(PlanDraft... drafts) {
        repairAnswers.addAll(List.of(drafts));
        return this;
    }

    public FakeLlmGateway queueRefresh(TextRefreshResult... results) {
        refreshAnswers.addAll(List.of(results));
        return this;
    }

    /** Lets a test pick the failure a chat turn sees, e.g. a timeout rather than a flat outage. */
    public FakeLlmGateway failNextChat(RuntimeException failure) {
        this.nextChatFailure = failure;
        return this;
    }

    public FakeLlmGateway failNextPlan(RuntimeException failure) {
        this.nextPlanFailure = failure;
        return this;
    }

    public FakeLlmGateway failNextRefresh(RuntimeException failure) {
        this.nextRefreshFailure = failure;
        return this;
    }

    public void reset() {
        chatAnswers.clear();
        planAnswers.clear();
        repairAnswers.clear();
        refreshAnswers.clear();
        nextChatFailure = null;
        nextPlanFailure = null;
        nextRefreshFailure = null;
        chatRequests.clear();
        planRequests.clear();
        repairRequests.clear();
        refreshRequests.clear();
    }

    @Override
    public ChatTurnResult chatTurn(ChatTurnRequest request) {
        chatRequests.add(request);
        if (nextChatFailure != null) {
            RuntimeException failure = nextChatFailure;
            nextChatFailure = null;
            throw failure;
        }
        if (chatAnswers.isEmpty()) {
            throw new IllegalStateException("FakeLlmGateway: no chat answer queued");
        }
        return chatAnswers.poll();
    }

    @Override
    public PlanDraftResult composePlan(PlanRequest request) {
        planRequests.add(request);
        if (nextPlanFailure != null) {
            RuntimeException failure = nextPlanFailure;
            nextPlanFailure = null;
            throw failure;
        }
        if (planAnswers.isEmpty()) {
            throw new IllegalStateException("FakeLlmGateway: no plan answer queued");
        }
        return new PlanDraftResult(planAnswers.poll(), new LlmUsage("fake", 10, 20, 5L));
    }

    @Override
    public PlanDraftResult repairPlan(RepairRequest request) {
        repairRequests.add(request);
        if (repairAnswers.isEmpty()) {
            throw new IllegalStateException("FakeLlmGateway: no repair answer queued");
        }
        return new PlanDraftResult(repairAnswers.poll(), new LlmUsage("fake", 10, 20, 5L));
    }

    @Override
    public TextRefreshResult refreshTexts(TextRefreshRequest request) {
        refreshRequests.add(request);
        if (nextRefreshFailure != null) {
            RuntimeException failure = nextRefreshFailure;
            nextRefreshFailure = null;
            throw failure;
        }
        if (refreshAnswers.isEmpty()) {
            throw new IllegalStateException("FakeLlmGateway: no refresh answer queued");
        }
        return refreshAnswers.poll();
    }
}
