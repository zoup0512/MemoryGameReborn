package com.memorygames.pk.ws;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window per-key rate limiter for the WS gateway (P1-3/P2-5):
 * registrations, auth attempts and general message floods are capped per
 * session and per remote IP. Deliberately allocation-light and in-process;
 * the admin plane keeps its own limiter.
 */
final class WsRateLimiter {

    private final ConcurrentHashMap<String, long[]> windows = new ConcurrentHashMap<>();
    private final long windowMs;
    private final int max;

    WsRateLimiter(long windowMs, int max) {
        this.windowMs = windowMs;
        this.max = max;
    }

    /** @return false when the key exceeded its budget inside the window. */
    boolean tryAcquire(String key) {
        long now = System.currentTimeMillis();
        long[] bucket = this.windows.compute(key, (k, v) -> {
            if (v == null || now - v[0] >= this.windowMs) {
                return new long[]{now, 1L};
            }
            v[1]++;
            return v;
        });
        if (this.windows.size() > 50000) {
            this.windows.clear(); // defensive: shed state under key spray
        }
        return bucket[1] <= this.max;
    }
}
