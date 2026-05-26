package run.cloudclaw.auth.security;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple per-IP rate limiter for the login endpoint.
 * Fix C3: Prevents brute-force attacks by limiting login attempts per IP address.
 * Uses a sliding window approach with configurable max requests and window duration.
 */
public class LoginRateLimiter {

    private final int maxRequests;
    private final long windowMillis;
    private final int maxEntries;
    private final Map<String, long[]> requestWindows = new ConcurrentHashMap<>();
    private final AtomicLong approximateSize = new AtomicLong(0);

    /**
     * @param maxRequests  maximum number of requests allowed in the window
     * @param windowMillis window duration in milliseconds
     */
    public LoginRateLimiter(int maxRequests, long windowMillis) {
        this(maxRequests, windowMillis, 10000);
    }

    /**
     * @param maxRequests  maximum number of requests allowed in the window
     * @param windowMillis window duration in milliseconds
     * @param maxEntries   maximum number of tracked IP entries
     */
    public LoginRateLimiter(int maxRequests, long windowMillis, int maxEntries) {
        this.maxRequests = maxRequests;
        this.windowMillis = windowMillis;
        this.maxEntries = maxEntries;
    }

    /**
     * Try to acquire a permit for the given key (IP address).
     *
     * @return true if the request is allowed, false if rate limit exceeded
     */
    public boolean tryAcquire(String key) {
        long now = Instant.now().toEpochMilli();
        // Reject new IPs if we've reached the max entries limit
        if (!requestWindows.containsKey(key) && approximateSize.get() >= maxEntries) {
            return false;
        }
        long[] timestamps = requestWindows.computeIfAbsent(key, k -> {
            approximateSize.incrementAndGet();
            return new long[maxRequests];
        });

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
                approximateSize.decrementAndGet();
                return true;
            }
        });
    }
}
