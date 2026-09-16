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
    void lockIsReleased_afterTheActionThrows() {
        SessionLocks locks = new SessionLocks();
        UUID token = UUID.randomUUID();
        String expectedResult = "free again";

        assertThatThrownBy(() -> locks.withLock(token, () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(locks.withLock(token, () -> expectedResult)).isEqualTo(expectedResult);
    }

    /**
     * A token's lock must outlive the call that created it. Dropping it when the last visible holder
     * released let a thread take the lock in the window between {@code unlock()} and the removal; the
     * removal then evicted a held lock and the next caller created a fresh one and entered alongside.
     */
    @Test
    void aTokenKeepsOneLockForever_soNoCallerCanBeHandedAFreshOne() {
        SessionLocks locks = new SessionLocks();
        UUID token = UUID.randomUUID();
        int expectedTrackedLocks = 1;

        locks.withLock(token, () -> null);
        locks.withLock(token, () -> null);

        assertThat(locks.tracked()).isEqualTo(expectedTrackedLocks);
    }

    @Test
    void release_forgetsATokenOnceItsSessionIsGone() {
        SessionLocks locks = new SessionLocks();
        UUID token = UUID.randomUUID();
        locks.withLock(token, () -> null);

        locks.release(token);

        assertThat(locks.tracked()).isZero();
    }
}
