package com.myhive.backend.ai.graph;

import com.myhive.backend.ai.graph.nodes.ChatTurnNode;
import com.myhive.backend.ai.graph.nodes.ComposeNode;
import com.myhive.backend.ai.graph.nodes.FallbackNode;
import com.myhive.backend.ai.graph.nodes.PersistResultNode;
import com.myhive.backend.ai.graph.nodes.RepairNode;
import com.myhive.backend.ai.graph.nodes.SelectNode;
import com.myhive.backend.ai.graph.nodes.SnapshotCatalogNode;
import com.myhive.backend.ai.graph.nodes.ValidateNode;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Builds the one planner graph the whole application shares; the nodes themselves are stateless beans. */
@Configuration
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
     * The providers are handed over unresolved on purpose: the service that implements the sinks depends
     * on {@link PlannerGraph}, so resolving one here would close a constructor-injection cycle.
     */
    @Bean
    public PersistResultNode persistResultNode(ObjectProvider<PersistResultNode.GenerationResultSink> sinks) {
        return new PersistResultNode(sinks);
    }

    @Bean
    public SelectNode selectNode(ObjectProvider<SelectNode.SelectionSink> sinks) {
        return new SelectNode(sinks);
    }
}
