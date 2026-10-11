package com.learning.learning.service;

import com.learning.learning.config.AssistantProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-user request caps for the help assistant: a rolling minute window (burst) and a
 * rolling day window (budget). In-memory and per instance, which is enough for a single
 * Render container; the point is to stop one account from running up the API bill, not
 * to be exact across restarts.
 */
@Component
public class AssistantRateLimiter {

    @Autowired
    private AssistantProperties properties;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * Records a request for {@code username} and returns a user-facing reason when it
     * must be refused, or empty when it may proceed.
     */
    public Optional<String> tryAcquire(String username) {
        Window w = windows.computeIfAbsent(username, k -> new Window());
        synchronized (w) {
            Instant now = Instant.now();
            if (Duration.between(w.minuteStart, now).toSeconds() >= 60) {
                w.minuteStart = now;
                w.minuteCount = 0;
            }
            if (Duration.between(w.dayStart, now).toHours() >= 24) {
                w.dayStart = now;
                w.dayCount = 0;
            }
            if (w.minuteCount >= properties.getPerMinuteLimit()) {
                return Optional.of("You are sending questions a little fast. Please wait a minute and try again.");
            }
            if (w.dayCount >= properties.getPerDayLimit()) {
                return Optional.of("You have reached today's limit for the help assistant. It resets tomorrow. "
                        + "In the meantime the written guides are at /help/.");
            }
            w.minuteCount++;
            w.dayCount++;
            return Optional.empty();
        }
    }

    private static class Window {
        Instant minuteStart = Instant.now();
        int minuteCount = 0;
        Instant dayStart = Instant.now();
        int dayCount = 0;
    }
}
