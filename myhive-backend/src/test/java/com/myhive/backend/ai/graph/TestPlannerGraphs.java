package com.myhive.backend.ai.graph;

import com.myhive.backend.ai.catalog.CatalogSnapshotter;
import com.myhive.backend.ai.edit.GenerationEditSink;
import com.myhive.backend.ai.edit.PackageEditor;
import com.myhive.backend.ai.edit.TextRefresher;
import com.myhive.backend.ai.graph.nodes.ApplyEditsNode;
import com.myhive.backend.ai.graph.nodes.ChatTurnNode;
import com.myhive.backend.ai.graph.nodes.ComposeNode;
import com.myhive.backend.ai.graph.nodes.FallbackNode;
import com.myhive.backend.ai.graph.nodes.PersistResultNode;
import com.myhive.backend.ai.graph.nodes.RepairNode;
import com.myhive.backend.ai.graph.nodes.SelectNode;
import com.myhive.backend.ai.graph.nodes.SnapshotCatalogNode;
import com.myhive.backend.ai.graph.nodes.ValidateNode;
import com.myhive.backend.ai.llm.FakeLlmGateway;
import com.myhive.backend.ai.plan.FallbackPlanComposer;
import com.myhive.backend.ai.plan.PlanAssembler;
import com.myhive.backend.ai.plan.PlanValidator;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.MemorySaver;

/** Builds a real planner graph around a scripted gateway; shared by the graph, service and saver tests. */
public final class TestPlannerGraphs {

    /**
     * For the tests that never take the edit branch: the batch is accepted and the parent id handed back,
     * which is exactly what the production node does when no sink bean is around.
     */
    private static final GenerationEditSink NO_EDITS = (parentGenerationId, plan, report, usage) -> parentGenerationId;

    private TestPlannerGraphs() {}

    public static PlannerGraph inMemory(FakeLlmGateway llm, CatalogSnapshotter snapshotter,
            PersistResultNode.GenerationResultSink resultSink, SelectNode.SelectionSink selectionSink) {
        return inMemory(llm, snapshotter, resultSink, selectionSink, NO_EDITS);
    }

    public static PlannerGraph inMemory(FakeLlmGateway llm, CatalogSnapshotter snapshotter,
            PersistResultNode.GenerationResultSink resultSink, SelectNode.SelectionSink selectionSink,
            GenerationEditSink editSink) {
        return withSaver(llm, snapshotter, resultSink, selectionSink, editSink, new MemorySaver());
    }

    public static PlannerGraph withSaver(FakeLlmGateway llm, CatalogSnapshotter snapshotter,
            PersistResultNode.GenerationResultSink resultSink, SelectNode.SelectionSink selectionSink,
            BaseCheckpointSaver saver) {
        return withSaver(llm, snapshotter, resultSink, selectionSink, NO_EDITS, saver);
    }

    public static PlannerGraph withSaver(FakeLlmGateway llm, CatalogSnapshotter snapshotter,
            PersistResultNode.GenerationResultSink resultSink, SelectNode.SelectionSink selectionSink,
            GenerationEditSink editSink, BaseCheckpointSaver saver) {
        PlanValidator validator = new PlanValidator();
        PlanAssembler assembler = new PlanAssembler();
        PlannerGraph.Nodes nodes = new PlannerGraph.Nodes(
                new ChatTurnNode(llm),
                new SnapshotCatalogNode(snapshotter),
                new ComposeNode(llm),
                new ValidateNode(validator, assembler),
                new RepairNode(llm),
                new FallbackNode(new FallbackPlanComposer()),
                new PersistResultNode(resultSink),
                new SelectNode(selectionSink),
                new ApplyEditsNode(new PackageEditor(validator, assembler), new TextRefresher(llm), editSink));
        return new PlannerGraph(nodes, saver);
    }
}
