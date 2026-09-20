package com.myhive.backend.ai.llm;

/** The only seam to the language model: chat turns and plan composition/repair. */
public interface LlmGateway {

    ChatTurnResult chatTurn(ChatTurnRequest request);

    PlanDraftResult composePlan(PlanRequest request);

    PlanDraftResult repairPlan(RepairRequest request);
}
