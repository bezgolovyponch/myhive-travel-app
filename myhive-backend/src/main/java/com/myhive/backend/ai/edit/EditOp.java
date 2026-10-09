package com.myhive.backend.ai.edit;

/**
 * The kind of change asked of the plan. {@code MOVE} keeps the activity and puts it on another day: it
 * names that day in the request's {@code dayNumber}.
 */
public enum EditOp {
    ADD, REMOVE, REPLACE, MOVE
}
