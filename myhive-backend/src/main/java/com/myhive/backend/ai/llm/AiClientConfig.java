package com.myhive.backend.ai.llm;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Registers {@link AiProperties} and the executor the model calls block on. The {@code ChatModel}
 * bean itself comes from Spring AI's OpenAI autoconfiguration (pointed at DashScope via
 * {@code spring.ai.openai.base-url}), so there is deliberately no client bean here.
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiClientConfig {

    /** Room for two generations writing their copy three packages at a time, plus the chat turns beside them. */
    private static final int LLM_CALL_THREADS = 8;
    private static final int PLAN_TEXTS_THREADS = 6;
    private static final int LLM_CALL_QUEUE_CAPACITY = 8;

    /**
     * The pool {@link SpringAiLlmGateway} runs its blocking HTTP calls on.
     *
     * <p>Not the ForkJoinPool common pool: {@code CompletableFuture.ASYNC_POOL} is
     * {@code ForkJoinPool.asyncCommonPool()}, whose parallelism is {@code cores - 1} and is only
     * bumped up when that is exactly zero -- so a 2-vCPU host gets a single worker. A blocking read
     * inside a ForkJoin task gets no compensation thread, so one chat turn plus a planner job would
     * queue behind each other and hit {@code get(timeout)} without ever having sent a request,
     * surfacing as a bogus "timed out" {@link LlmUnavailableException}.
     *
     * <p>And deliberately NOT the planner's {@code aiTaskExecutor}: a planner job running on that
     * 2-thread pool submits its model call here, so sharing the pool would let a full pool wait on
     * itself and deadlock. Separate pool, own bounded queue, fail fast when saturated.
     */
    @Bean(name = "llmCallExecutor")
    public Executor llmCallExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(LLM_CALL_THREADS);
        executor.setMaxPoolSize(LLM_CALL_THREADS);
        executor.setQueueCapacity(LLM_CALL_QUEUE_CAPACITY);
        executor.setThreadNamePrefix("llm-call-");
        // Saturation must surface as LlmUnavailableException, never as the caller (a request
        // thread) running the blocking call itself, which CallerRunsPolicy would do.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // An in-flight model call is already past its own timeout budget by the time we shut down;
        // waiting for it would only delay the shutdown.
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }

    /**
     * Where {@link com.myhive.backend.ai.plan.PlanTextWriter} fans a generation's copy out, one package
     * per task. Not {@link #llmCallExecutor()}: a task here blocks on a call submitted there, and a pool
     * waiting on itself is a deadlock. Sized for two generations at once; past that the job thread
     * writes the package itself (CallerRuns) - later rather than never, and never a rejection.
     */
    @Bean(name = "planTextsExecutor")
    public Executor planTextsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(PLAN_TEXTS_THREADS);
        executor.setMaxPoolSize(PLAN_TEXTS_THREADS);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("plan-texts-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}
