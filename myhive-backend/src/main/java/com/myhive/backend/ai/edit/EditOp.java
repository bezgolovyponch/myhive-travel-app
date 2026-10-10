package com.myhive.backend.ai.edit;

/**
 * The kind of change asked of the plan. {@code MOVE} keeps the activity and puts it on another day: it
 * names that day in the request's {@code dayNumber}. {@code ADD_EXTRA}/{@code REMOVE_EXTRA} choose or drop one
 * optional extra of an activity the package already holds, named in the request's {@code extra}; they never
 * move anything.
 */
public enum EditOp {
    ADD, REMOVE, REPLACE, MOVE, ADD_EXTRA, REMOVE_EXTRA;

    /** True for the ops that change an activity's extras rather than the schedule. */
    public boolean isExtraOp() {
        return this == ADD_EXTRA || this == REMOVE_EXTRA;
    }
}
