package com.myhive.backend.ai.llm;

/** The only seam to the language model: chat turns, plan composition/repair, the copy of a new plan and the post-edit text refresh. */
public interface LlmGateway {

    ChatTurnResult chatTurn(ChatTurnRequest request);

    PlanDraftResult composePlan(PlanRequest request);

    PlanDraftResult repairPlan(RepairRequest request);

    TextRefreshResult refreshTexts(TextRefreshRequest request);

    /** The copy of a freshly composed plan, written after its structure is validated and priced. */
    PlanTextsResult writeTexts(PlanTextsRequest request);
}
