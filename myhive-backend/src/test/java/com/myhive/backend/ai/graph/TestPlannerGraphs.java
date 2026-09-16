package com.myhive.backend.ai.graph;

import com.myhive.backend.ai.catalog.CatalogSnapshotter;
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
final class TestPlannerGraphs {

    private TestPlannerGraphs() {}

    static PlannerGraph inMemory(FakeLlmGateway llm, CatalogSnapshotter snapshotter,
            PersistResultNode.GenerationResultSink resultSink, SelectNode.SelectionSink selectionSink) {
        return withSaver(llm, snapshotter, resultSink, selectionSink, new MemorySaver());
    }

    static PlannerGraph withSaver(FakeLlmGateway llm, CatalogSnapshotter snapshotter,
            PersistResultNode.GenerationResultSink resultSink, SelectNode.SelectionSink selectionSink,
            BaseCheckpointSaver saver) {
        PlannerGraph.Nodes nodes = new PlannerGraph.Nodes(
                new ChatTurnNode(llm),
                new SnapshotCatalogNode(snapshotter),
                new ComposeNode(llm),
                new ValidateNode(new PlanValidator(), new PlanAssembler()),
                new RepairNode(llm),
                new FallbackNode(new FallbackPlanComposer()),
                new PersistResultNode(resultSink),
                new SelectNode(selectionSink));
        return new PlannerGraph(nodes, saver);
    }
}
