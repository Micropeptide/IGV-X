package org.broad.igv.diagnostic;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * IGV-X regression tests for the exception rate-limiter: identical repeated
 * exceptions must be deduplicated (one log per cooldown window with a
 * suppressed-count summary), while distinct exceptions must each pass.
 */
public class ExceptionRateLimiterTest {

    @Test
    public void firstOccurrenceLogs() {
        ExceptionRateLimiter limiter = new ExceptionRateLimiter(60_000);
        ExceptionRateLimiter.Decision d = limiter.decide(new IllegalStateException("boom"));
        assertTrue(d.log);
        assertEquals(0, d.suppressedSinceLastLog);
    }

    @Test
    public void identicalRepeatsAreSuppressed() {
        ExceptionRateLimiter limiter = new ExceptionRateLimiter(60_000);
        limiter.decide(new IllegalStateException("boom"));
        ExceptionRateLimiter.Decision d2 = limiter.decide(new IllegalStateException("boom"));
        assertFalse(d2.log);
        ExceptionRateLimiter.Decision d3 = limiter.decide(new IllegalStateException("boom"));
        assertFalse(d3.log);
    }

    @Test
    public void differentExceptionsPassIndependently() {
        ExceptionRateLimiter limiter = new ExceptionRateLimiter(60_000);
        limiter.decide(new IllegalStateException("boom"));
        assertTrue(limiter.decide(new IllegalArgumentException("different")).log);
    }

    @Test
    public void windowExpiryEmitsSummary() throws Exception {
        ExceptionRateLimiter limiter = new ExceptionRateLimiter(50);
        limiter.decide(new IllegalStateException("boom"));
        limiter.decide(new IllegalStateException("boom"));
        limiter.decide(new IllegalStateException("boom"));
        Thread.sleep(80);
        ExceptionRateLimiter.Decision d = limiter.decide(new IllegalStateException("boom"));
        assertTrue("summary occurrence after window should log", d.log);
        assertEquals("should report 2 suppressed since last log", 2, d.suppressedSinceLastLog);
    }

    @Test
    public void rootCauseSignatureIgnoresWrapper() {
        ExceptionRateLimiter limiter = new ExceptionRateLimiter(60_000);
        RuntimeException inner = new IllegalStateException("boom");
        limiter.decide(inner);
        // Wrapped in a different outer type but same root cause -> same signature
        assertFalse(limiter.decide(new RuntimeException("wrapped", inner)).log);
    }
}
