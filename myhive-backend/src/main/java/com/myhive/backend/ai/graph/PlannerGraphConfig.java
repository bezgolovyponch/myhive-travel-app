package com.myhive.backend.ai.graph;

import com.myhive.backend.ai.graph.nodes.ChatTurnNode;
import com.myhive.backend.ai.graph.nodes.ComposeNode;
import com.myhive.backend.ai.graph.nodes.FallbackNode;
import com.myhive.backend.ai.graph.nodes.PersistResultNode;
import com.myhive.backend.ai.graph.nodes.RepairNode;
import com.myhive.backend.ai.graph.nodes.SelectNode;
import com.myhive.backend.ai.graph.nodes.SnapshotCatalogNode;
import com.myhive.backend.ai.graph.nodes.ValidateNode;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Builds the one planner graph the whole application shares; the nodes themselves are stateless beans. */
@Configuration
@Slf4j
public class PlannerGraphConfig {

    @Bean
    public PlannerGraph plannerGraph(ChatTurnNode chatTurn, SnapshotCatalogNode snapshotCatalog, ComposeNode compose,
            ValidateNode validate, RepairNode repair, FallbackNode fallback, PersistResultNode persistResult,
            SelectNode select, BaseCheckpointSaver checkpointSaver) {
        PlannerGraph.Nodes nodes = new PlannerGraph.Nodes(chatTurn, snapshotCatalog, compose, validate, repair,
                fallback, persistResult, select);
        return new PlannerGraph(nodes, checkpointSaver);
    }

    /**
     * The sink is whichever service stores generations. It is looked up lazily rather than injected so
     * that the graph still builds before that service exists; a missing sink is loud, never silent.
     */
    @Bean
    public PersistResultNode persistResultNode(ObjectProvider<PersistResultNode.GenerationResultSink> sinks) {
        return new PersistResultNode(sinks.getIfUnique(() -> (generationId, plan, degraded, usage, attempt) ->
                log.warn("planner result dropped generation={}: no GenerationResultSink bean", generationId)));
    }

    @Bean
    public SelectNode selectNode(ObjectProvider<SelectNode.SelectionSink> sinks) {
        return new SelectNode(sinks.getIfUnique(() -> (generationId, key) ->
                log.warn("planner selection dropped generation={}: no SelectionSink bean", generationId)));
    }
}
