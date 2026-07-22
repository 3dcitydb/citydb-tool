/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.util;

import org.slf4j.Logger;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Time-based progress reporter for long-running close-phase loops.
 * <p>
 * Emits an INFO line at most once per {@link #LOG_INTERVAL_NANOS} plus a
 * final line when the last item completes, so slow per-item work — large
 * texture atlases, big grid cells — still produces regular console feedback
 * while fast loops don't flood the log. Count-based reporting (every N
 * items) behaves inversely on both ends: fast items spam the console and
 * slow items leave it silent for minutes, which is exactly the symptom this
 * replaces.
 * <p>
 * Thread-safe: {@link #step()} may be called concurrently from a parallel
 * fan-out (ForkJoinPool workers); the interval gate is a CAS so at most one
 * thread logs per elapsed interval.
 */
public final class ProgressLogger {
    private static final long LOG_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(10);

    private final Logger logger;
    private final String message;
    private final int total;
    private final AtomicInteger counter = new AtomicInteger();
    private final AtomicLong nextLogNanos;

    /**
     * @param logger  target logger (the caller's, so the log line carries the
     *                originating class)
     * @param message SLF4J format string with two {@code {}} placeholders,
     *                filled with items done and total
     * @param total   total number of items the loop will process
     */
    public ProgressLogger(Logger logger, String message, int total) {
        this.logger = Objects.requireNonNull(logger, "The logger must not be null.");
        this.message = Objects.requireNonNull(message, "The message must not be null.");
        this.total = total;
        this.nextLogNanos = new AtomicLong(System.nanoTime() + LOG_INTERVAL_NANOS);
    }

    /**
     * Record one completed item and log if this was the final item or the
     * reporting interval has elapsed since the last log.
     */
    public void step() {
        int done = counter.incrementAndGet();
        if (done == total) {
            logger.info(message, done, total);
            return;
        }

        long now = System.nanoTime();
        long next = nextLogNanos.get();
        if (now >= next && nextLogNanos.compareAndSet(next, now + LOG_INTERVAL_NANOS)) {
            logger.info(message, done, total);
        }
    }
}
