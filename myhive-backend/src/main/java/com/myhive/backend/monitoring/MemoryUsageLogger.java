package com.myhive.backend.monitoring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Logs the JVM's memory split on a fixed cadence. A container OOM kill (2026-10-02, 2026-10-06)
 * leaves no trace of its own in the application log, so this line is the only record of how close
 * the heap, metaspace and code cache were to the Dockerfile's ceilings when an instance vanished.
 * INFO on purpose: prod keeps {@code com.myhive.backend.monitoring} at INFO under its WARN default.
 */
@Component
@Slf4j
public class MemoryUsageLogger {

    /**
     * The fallback matters: the test classpath's {@code application.properties} shadows the main one,
     * so a {@code @SpringBootTest} context only sees the property through this default.
     */
    private static final String INTERVAL = "${app.monitoring.memory-log-interval:PT10M}";

    @Scheduled(fixedDelayString = INTERVAL, initialDelayString = INTERVAL)
    public void logUsage() {
        log.info(JvmMemorySnapshot.capture().summary());
    }
}
