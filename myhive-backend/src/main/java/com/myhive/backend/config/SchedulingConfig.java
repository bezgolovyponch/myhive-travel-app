package com.myhive.backend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Every {@code @Scheduled} job in the application, switched as one unit.
 *
 * <p>The jobs assume a single process that owns its database: {@code VoteSessionScheduler} closes
 * expired votes and their result email goes out from whichever process got there first,
 * {@code PlanGenerationService} fails QUEUED generations older than its own start as orphans, the
 * nightly crons delete rows. A second process on the same database - a developer machine on the
 * {@code proddb} profile - must therefore not tick at all, or it closes votes whose result email it
 * will never send. {@code app.scheduling.enabled} (env {@code SCHEDULING_ENABLED}) is that kill
 * switch; absent means on.
 */
@Configuration
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
public class SchedulingConfig {}
