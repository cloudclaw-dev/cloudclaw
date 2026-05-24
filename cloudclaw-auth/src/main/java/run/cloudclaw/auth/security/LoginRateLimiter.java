package run.cloudclaw.auth.security;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple per-IP rate limiter for the login endpoint.
 * Fix C3: Prevents brute-force attacks by limiting login attempts per IP address.
 * Uses a sliding window approach with configurable max requests and window duration.
 */
public class LoginRateLimiter {

    private final int maxRequests;
    private final long windowMillis;
    private final Map<String, long[]> requestWindows = new ConcurrentHashMap<>();

    /**
     * @param maxRequests  maximum number of requests allowed in the window
     * @param windowMillis window duration in milliseconds
     */
    public LoginRateLimiter(int maxRequests, long windowMillis) {
        this.maxRequests = maxRequests;
        this.windowMillis = windowMillis;
    }

    /**
     * Try to acquire a permit for the given key (IP address).
     *
     * @return true if the request is allowed, false if rate limit exceeded
     */
    public boolean tryAcquire(String key) {
        long now = Instant.now().toEpochMilli();
        long[] timestamps = requestWindows.computeIfAbsent(key, k -> new long[maxRequests]);

        synchronized (timestamps) {
            // Find an expired slot or an unused slot
            for (int i = 0; i < timestamps.length; i++) {
                if (timestamps[i] == 0 || (now - timestamps[i]) > windowMillis) {
                    timestamps[i] = now;
                    return true;
                }
            }
            // All slots are within the window — rate limit exceeded
            return false;
        }
    }

    /**
     * Clean up expired entries to prevent memory leaks.
     */
    public void cleanup() {
        long now = Instant.now().toEpochMilli();
        requestWindows.entrySet().removeIf(entry -> {
            long[] timestamps = entry.getValue();
            synchronized (timestamps) {
                for (long ts : timestamps) {
                    if (ts != 0 && (now - ts) <= windowMillis) {
                        return false;
                    }
                }
                return true;
            }
        });
    }
}
