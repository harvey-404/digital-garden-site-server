package com.harvey.digitalgarden.game.semantic.ws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-session guess interval + per-fp sliding-window rate limit.
 */
@Component
public class GuessRateLimiter {

    private final long guessIntervalMs;
    private final int fpGuessesPerMinute;

    private final ConcurrentHashMap<String, Long> lastGuessBySession = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> sessionLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Deque<Long>> guessesByFp = new ConcurrentHashMap<>();

    public GuessRateLimiter(
            @Value("${app.game.semantic.guess-interval-ms}") long guessIntervalMs,
            @Value("${app.game.semantic.fp-guesses-per-minute}") int fpGuessesPerMinute) {
        this.guessIntervalMs = guessIntervalMs;
        this.fpGuessesPerMinute = fpGuessesPerMinute;
    }

    /**
     * @return true if the guess is allowed (and recorded); false if rate-limited
     */
    public boolean allow(String sessionId, String fp) {
        long now = System.currentTimeMillis();
        Object sessionLock = sessionLocks.computeIfAbsent(sessionId, k -> new Object());
        synchronized (sessionLock) {
            Long last = lastGuessBySession.get(sessionId);
            if (last != null && now - last < guessIntervalMs) {
                return false;
            }

            Deque<Long> window = guessesByFp.computeIfAbsent(fp, k -> new ArrayDeque<>());
            synchronized (window) {
                prune(window, now);
                if (window.size() >= fpGuessesPerMinute) {
                    return false;
                }
                window.addLast(now);
            }

            lastGuessBySession.put(sessionId, now);
            return true;
        }
    }

    public void onSessionClosed(String sessionId) {
        if (sessionId != null) {
            lastGuessBySession.remove(sessionId);
            sessionLocks.remove(sessionId);
        }
    }

    private void prune(Deque<Long> window, long now) {
        long cutoff = now - 60_000L;
        while (!window.isEmpty() && window.peekFirst() < cutoff) {
            window.removeFirst();
        }
    }
}
