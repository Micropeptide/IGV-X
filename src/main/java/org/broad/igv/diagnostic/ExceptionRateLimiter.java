/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2007-2026 Broad Institute, IGV-X contributors
 */
package org.broad.igv.diagnostic;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IGV-X: rate-limits repeated exceptions so a storm of identical errors
 * (e.g. the same missing chromosome or network failure logged thousands of
 * times while painting a large session) does not flood logs and the UI.
 *
 * The first occurrence of a signature passes; subsequent identical
 * occurrences within the cooldown window are suppressed. When the window
 * expires, one summary line (with the suppressed count) is emitted and the
 * window restarts.
 */
public class ExceptionRateLimiter {

    private static final long DEFAULT_COOLDOWN_MS = 60_000L;

    private static class Entry {
        long windowStart;
        int suppressed;

        Entry(long now) {
            this.windowStart = now;
            this.suppressed = 0;
        }
    }

    private final long cooldownMs;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public ExceptionRateLimiter(long cooldownMs) {
        this.cooldownMs = cooldownMs > 0 ? cooldownMs : DEFAULT_COOLDOWN_MS;
    }

    public ExceptionRateLimiter() {
        this(DEFAULT_COOLDOWN_MS);
    }

    /**
     * Decide whether this exception should be logged now, and how many
     * identical occurrences were suppressed since the last logged one.
     * Returns true for the first occurrence and for one summary occurrence
     * per cooldown window; suppressedSinceLastLog reports how many were
     * skipped during the previous window (0 for the first occurrence).
     */
    public Decision decide(Throwable t) {
        String sig = signature(t);
        long now = System.currentTimeMillis();
        boolean fresh = !entries.containsKey(sig);
        Entry entry = entries.computeIfAbsent(sig, k -> new Entry(now));
        synchronized (entry) {
            if (fresh || now - entry.windowStart >= cooldownMs) {
                int suppressedBefore = entry.suppressed;
                // New signature, or new window: allow this one, carrying the
                // suppressed count from the old window so the caller can emit
                // a summary line.
                entry.windowStart = now;
                entry.suppressed = 0;
                return new Decision(true, suppressedBefore);
            }
            entry.suppressed++;
            return new Decision(false, 0);
        }
    }

    /**
     * Reset all state (mainly for tests and session clear).
     */
    public void reset() {
        entries.clear();
    }

    /** Result of {@link #decide(Throwable)}. */
    public static class Decision {
        public final boolean log;
        public final int suppressedSinceLastLog;

        Decision(boolean log, int suppressedSinceLastLog) {
            this.log = log;
            this.suppressedSinceLastLog = suppressedSinceLastLog;
        }
    }

    /**
     * Stable signature for an exception: class name + first frame of the
     * message stack (root cause class + message). Using the top-level message
     * would split truly identical errors that differ only in wrapped text.
     */
    static String signature(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String cls = root.getClass().getName();
        String msg = root.getMessage();
        if (msg != null && msg.length() > 120) {
            msg = msg.substring(0, 120);
        }
        return cls + "|" + (msg == null ? "<null>" : msg);
    }
}
