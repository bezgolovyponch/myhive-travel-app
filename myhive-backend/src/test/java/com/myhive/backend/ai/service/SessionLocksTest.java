package com.myhive.backend.ai.service;

import com.myhive.backend.ai.exception.AiConflictException;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionLocksTest {

    @Test
    void secondCallerOnSameToken_getsSessionBusy() throws Exception {
        SessionLocks locks = new SessionLocks();
        UUID token = UUID.randomUUID();
        String expectedOtherResult = "other";
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread holder = new Thread(() -> {
            try {
                locks.withLock(token, () -> {
                    inside.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                });
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        holder.start();
        inside.await();

        assertThatThrownBy(() -> locks.withLock(token, () -> null)).isInstanceOf(AiConflictException.class)
                .hasFieldOrPropertyWithValue("code", "SESSION_BUSY");
        assertThat(locks.withLock(UUID.randomUUID(), () -> expectedOtherResult)).isEqualTo(expectedOtherResult);

        release.countDown();
        holder.join();
        assertThat(failure.get()).isNull();
    }

    @Test
    void lockIsReleasedAndForgotten_afterTheActionThrows() {
        SessionLocks locks = new SessionLocks();
        UUID token = UUID.randomUUID();
        String expectedResult = "free again";

        assertThatThrownBy(() -> locks.withLock(token, () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(locks.withLock(token, () -> expectedResult)).isEqualTo(expectedResult);
        assertThat(locks.tracked()).isZero();
    }
}
