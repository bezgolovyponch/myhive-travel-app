package com.myhive.backend.ai;

import com.myhive.backend.ai.graph.nodes.PersistResultNode;
import com.myhive.backend.ai.graph.nodes.SelectNode;
import com.myhive.backend.ai.service.PlanGenerationService;
import com.myhive.backend.config.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two wiring invariants the planner silently depends on, asserted against the real context.
 *
 * <p>{@link PersistResultNode} and {@link SelectNode} resolve their sink with {@code getIfUnique()}:
 * a second candidate bean does not fail the context, it falls back to a logging no-op and every plan
 * is dropped on the floor. And {@link PlanGenerationService#runJob} calls its own {@code fail(...)}
 * through the bean's proxy, which only exists while the bean is proxied at all.
 */
@SpringBootTest
@Import({TestSecurityConfig.class, AiTestConfig.class})
class AiWiringTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void exactlyOneBeanImplementsEachGraphSink() {
        String[] resultSinks = context.getBeanNamesForType(PersistResultNode.GenerationResultSink.class);
        String[] selectionSinks = context.getBeanNamesForType(SelectNode.SelectionSink.class);

        assertThat(resultSinks).hasSize(1);
        assertThat(selectionSinks).hasSize(1);
        assertThat(context.getBean(resultSinks[0])).isInstanceOf(PlanGenerationService.class);
        assertThat(context.getBean(selectionSinks[0])).isInstanceOf(PlanGenerationService.class);
    }

    @Test
    void planGenerationServiceIsATransactionalProxy() {
        Object bean = context.getBean(PlanGenerationService.class);

        assertThat(AopUtils.isAopProxy(bean)).isTrue();
    }
}
