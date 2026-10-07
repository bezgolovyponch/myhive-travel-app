package com.myhive.backend.monitoring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class MemoryUsageLoggerTest {

    @Test
    void logUsage_writesOneSummaryLineOfTheRunningJvm(CapturedOutput output) {
        new MemoryUsageLogger().logUsage();

        assertThat(output.getOut())
                .containsPattern("JVM memory: heap \\d+/\\d+/(\\d+|\\?) MiB used/committed/max; "
                        + "metaspace \\d+ MiB \\(class space \\d+\\); code cache \\d+ MiB; "
                        + "heap\\+metaspace\\+code \\d+ MiB committed; \\d+ threads");
    }
}
