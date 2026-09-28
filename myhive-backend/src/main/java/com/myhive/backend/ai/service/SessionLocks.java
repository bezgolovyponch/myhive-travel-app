package com.myhive.backend.ai.service;

import com.myhive.backend.ai.exception.AiConflictException;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * One in-process lock per session token. A planner turn reads the checkpoint, resumes the graph and
 * writes it back, so two concurrent calls on the same chat would race on the same thread id. The
 * lock never waits: a second request is told the chat is busy rather than queueing behind a model
 * call it will outlive.
 *
 * <p>An entry is never evicted while the process lives, which is the point. Dropping it when the
 * last visible holder released looked tidy but let two callers into the section at once: between a
 * holder's {@code unlock()} and its {@code remove()} another thread can take the very same lock, the
 * {@code remove()} then drops a lock that is held, and the next caller creates a fresh one and walks
 * straight in. ({@code hasQueuedThreads()} never guarded that - a zero-wait {@code tryLock} never
 * enqueues anyone.) The map is bounded by the number of sessions this process has served; the
 * session TTL cleanup calls {@link #release(UUID)} when a chat is gone for good.
 *
 * <p>Single-instance only, which is what the planner runs on today. A second backend replica would
 * need the lock in Postgres instead.
 */
@Component
public class SessionLocks {

    private final ConcurrentHashMap<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();

    public <T> T withLock(UUID token, Supplier<T> action) {
        ReentrantLock lock = locks.computeIfAbsent(token, t -> new ReentrantLock());
        if (!lock.tryLock()) {
            throw new AiConflictException("SESSION_BUSY", "Another request for this chat is still running");
        }
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    /** Forgets a token's lock. Only safe once the session itself is gone - an expired or deleted chat. */
    public void release(UUID token) {
        locks.remove(token);
    }

    /** Locks held in the map; one per session token this process has served. */
    int tracked() {
        return locks.size();
    }
}
