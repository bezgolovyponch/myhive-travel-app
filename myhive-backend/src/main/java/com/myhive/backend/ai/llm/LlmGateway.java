package com.myhive.backend.ai.llm;

/** The only seam to the language model: chat turns, plan composition/repair and the post-edit text refresh. */
public interface LlmGateway {

    ChatTurnResult chatTurn(ChatTurnRequest request);

    PlanDraftResult composePlan(PlanRequest request);

    PlanDraftResult repairPlan(RepairRequest request);

    TextRefreshResult refreshTexts(TextRefreshRequest request);
}
