package com.learning.learning.security;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window rate limiter keyed by an arbitrary string
 * (typically the client IP). Used to cap how often a single source can submit
 * public forms.
 *
 * Not distributed — state lives in this instance's heap, which is fine for a
 * single-node deployment. Stale keys are pruned lazily as they are queried.
 */
@Component
public class SubmissionRateLimiter {

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    /**
     * Records an attempt and reports whether it is allowed.
     *
     * @param key          identity to rate-limit (e.g. "charity-apply:1.2.3.4")
     * @param maxRequests  max allowed attempts within the window
     * @param windowMillis size of the sliding window in milliseconds
     * @return true if the attempt is within the limit, false if it should be blocked
     */
    public synchronized boolean allow(String key, int maxRequests, long windowMillis) {
        long now = System.currentTimeMillis();
        Deque<Long> timestamps = hits.computeIfAbsent(key, k -> new ArrayDeque<>());

        // Drop timestamps that have aged out of the window.
        while (!timestamps.isEmpty() && now - timestamps.peekFirst() > windowMillis) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= maxRequests) {
            return false;
        }

        timestamps.addLast(now);
        return true;
    }
}
