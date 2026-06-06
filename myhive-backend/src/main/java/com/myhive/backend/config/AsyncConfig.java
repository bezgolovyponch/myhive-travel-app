package com.myhive.backend.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Enables {@code @Async} support and provides a bounded executor for outbound email.
 *
 * <p>Email is sent over a blocking SSL SMTP connection (Resend, port 465), which can take
 * several seconds per message. Running it on the request thread made vote-session creation and
 * other email-triggering endpoints feel slow. Offloading the send to this pool lets the HTTP
 * response return immediately.
 *
 * <p>The pool is deliberately small and bounded to suit the modest production instance. When the
 * queue is full, {@link ThreadPoolExecutor.CallerRunsPolicy} makes the caller send synchronously
 * rather than dropping the email — back-pressure instead of silent loss.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "emailTaskExecutor")
    public Executor emailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("email-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // On shutdown (e.g. a Render redeploy) drain queued emails instead of dropping them.
        // Kept well under Render's ~30s SIGTERM grace so the rest of the context (DataSource,
        // Tomcat) still has time to shut down cleanly before SIGKILL.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        executor.initialize();
        return executor;
    }
}
