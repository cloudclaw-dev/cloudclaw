package run.cloudclaw.admin.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * In-memory Logback appender that keeps the latest log events in a ring buffer.
 * Used by AdminLogsController to expose recent logs via API.
 */
public class InMemoryLogAppender extends AppenderBase<ILoggingEvent> {

    private static final int DEFAULT_MAX_SIZE = 1000;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    private final ArrayBlockingQueue<Map<String, String>> buffer;
    private int maxSize = DEFAULT_MAX_SIZE;

    public InMemoryLogAppender() {
        this.buffer = new ArrayBlockingQueue<>(DEFAULT_MAX_SIZE);
    }

    @Override
    protected void append(ILoggingEvent event) {
        String message = event.getFormattedMessage();
        if (message == null) message = "";
        Map<String, String> entry = Map.of(
                "time", TIME_FMT.format(Instant.ofEpochMilli(event.getTimeStamp())),
                "level", event.getLevel() != null ? event.getLevel().toString().toLowerCase() : "info",
                "logger", event.getLoggerName() != null ? event.getLoggerName() : "",
                "message", message
        );
        buffer.offer(entry); // offer() discards if full (oldest already evicted)
        // Evict oldest if over capacity (edge case during concurrent access)
        while (buffer.size() > maxSize) {
            buffer.poll();
        }
    }

    /**
     * Get recent log entries, optionally filtered by level.
     */
    public List<Map<String, String>> getLogs(String level, int limit) {
        Level filterLevel = null;
        if (level != null && !level.isBlank()) {
            filterLevel = Level.toLevel(level.toUpperCase(), null);
        }

        List<Map<String, String>> result = new ArrayList<>();
        // Read all entries, then filter in reverse order (newest first)
        List<Map<String, String>> all = new ArrayList<>(buffer);
        Collections.reverse(all);
        for (Map<String, String> entry : all) {
            if (result.size() >= limit) break;
            if (filterLevel == null || matchesLevel(entry.get("level"), filterLevel)) {
                result.add(entry);
            }
        }
        return result;
    }

    private boolean matchesLevel(String levelStr, Level filterLevel) {
        try {
            Level entryLevel = Level.toLevel(levelStr.toUpperCase());
            return entryLevel.isGreaterOrEqual(filterLevel);
        } catch (Exception e) {
            return false;
        }
    }

    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }
}
