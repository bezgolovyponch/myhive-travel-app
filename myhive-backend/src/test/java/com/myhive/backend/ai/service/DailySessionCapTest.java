package com.myhive.backend.ai.service;

import com.myhive.backend.ai.exception.AiLimitException;
import com.myhive.backend.ai.llm.AiProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailySessionCapTest {

    /** Midnight UTC, so adding a day is unambiguously "tomorrow". */
    private Instant now = Instant.parse("2026-09-16T00:00:00Z");

    /** Reads {@link #now} on every call, so a test can move the day forward mid-test. */
    private final Clock clock = new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    };

    private DailySessionCap capWith(int dailySessionsPerIp) {
        AiProperties props = new AiProperties();
        props.setDailySessionsPerIp(dailySessionsPerIp);
        return new DailySessionCap(props, clock);
    }

    @Test
    void allowsUpToLimit_thenRejects() {
        int expectedLimit = 2;
        DailySessionCap cap = capWith(expectedLimit);

        for (int i = 0; i < expectedLimit; i++) {
            assertThatCode(() -> cap.check("h1")).doesNotThrowAnyException();
        }

        assertThatThrownBy(() -> cap.check("h1")).isInstanceOf(AiLimitException.class)
                .hasFieldOrPropertyWithValue("code", "SESSION_DAILY_LIMIT");
        assertThatCode(() -> cap.check("h2")).doesNotThrowAnyException();
    }

    @Test
    void theNextDay_startsFreshAndDropsYesterdaysCounters() {
        int expectedTrackedCounters = 1;
        DailySessionCap cap = capWith(1);
        cap.check("h1");
        cap.check("h2");
        assertThatThrownBy(() -> cap.check("h1")).isInstanceOf(AiLimitException.class);

        now = now.plus(Duration.ofDays(1));

        assertThatCode(() -> cap.check("h1")).doesNotThrowAnyException();
        assertThat(cap.tracked()).isEqualTo(expectedTrackedCounters);
    }
}
