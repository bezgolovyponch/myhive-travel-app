package com.myhive.backend.entity;

/** Whether a generation came out of the planner model or was produced by applying edits to one. */
public enum AiGenerationKind {
    GENERATED,
    EDITED
}
