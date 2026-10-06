package com.myhive.backend.ai.graph;

/** Why a parked thread is being resumed; written by the services before every {@code stream(resume())}. */
public enum ResumeReason {
    USER_MESSAGE, GENERATE, SELECT,
    /**
     * A tap in the trip draft (Add on a recommendation, Added ✓ or × on a line): the edit in state is applied
     * as it stands, with no chat turn and no model call.
     */
    EDIT
}
