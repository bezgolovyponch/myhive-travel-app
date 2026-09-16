package com.myhive.backend.ai.service;

import com.myhive.backend.ai.exception.AiLimitException;
import com.myhive.backend.ai.llm.AiProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Caps how many planner chats one network may start per UTC day. In memory on purpose: the counter
 * only has to make an abusive loop expensive, and a restart costing an attacker nothing is cheaper
 * than a table. Yesterday's counters are dropped on the first call of a new day, so the map never
 * grows beyond one day of distinct IP hashes.
 */
@Component
public class DailySessionCap {

    private final AiProperties props;
    private final Clock clock;
    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    @Autowired
    public DailySessionCap(AiProperties props) {
        this(props, Clock.systemUTC());
    }

    /** Test seam: a clock a test can move a day forward. */
    DailySessionCap(AiProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    public void check(String ipHash) {
        String today = LocalDate.now(clock).toString();
        counters.keySet().removeIf(key -> !key.endsWith(today));
        int count = counters.computeIfAbsent(ipHash + ":" + today, key -> new AtomicInteger()).incrementAndGet();
        if (count > props.getDailySessionsPerIp()) {
            throw new AiLimitException("SESSION_DAILY_LIMIT", "Too many planner chats started today from this network");
        }
    }

    /** Counters held right now; only today's survive a {@link #check(String)}. */
    int tracked() {
        return counters.size();
    }
}
