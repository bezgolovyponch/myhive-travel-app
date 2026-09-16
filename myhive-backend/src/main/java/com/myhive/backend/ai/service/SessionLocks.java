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
            if (!lock.hasQueuedThreads()) {
                locks.remove(token, lock);
            }
        }
    }

    /** Live lock count; a held or contended token keeps its entry, everything else is forgotten. */
    int tracked() {
        return locks.size();
    }
}
