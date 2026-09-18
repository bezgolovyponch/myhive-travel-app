package com.myhive.backend.ai.edit;

import com.myhive.backend.ai.llm.LlmUsage;
import com.myhive.backend.ai.plan.ComposedPlan;

import java.util.UUID;

/**
 * Implemented by the generation service; the graph never touches the database itself. Stores the plan an
 * edit batch produced as a new {@code EDITED} generation and returns its id.
 */
public interface GenerationEditSink {

    UUID edited(UUID parentGenerationId, ComposedPlan plan, EditReport report, LlmUsage usage);
}
