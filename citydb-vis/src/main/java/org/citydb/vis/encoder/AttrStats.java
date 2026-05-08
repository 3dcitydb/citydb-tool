/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.encoder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Thread-safe per-attribute statistics accumulator. Numeric attributes
 * collect count / min / max / sum / sum-of-squares; string attributes
 * collect a bounded frequency table (top-{@value #MAX_TRACKED_VALUES}
 * unique values). Updates are synchronized — contention is bounded by
 * (concurrent writer threads × attribute count) and each update is a
 * handful of arithmetic ops, so the simpler synchronized form beats
 * managing per-thread shards.
 * <p>
 * Consumed by {@link AttributeEncoder} to populate the I3S layer's
 * {@code layer.statisticsInfo[]} entries plus the per-attribute
 * {@code layers/0/statistics/f_K/0/index.json} resource files. ArcGIS
 * Pro reads these to drive its attribute-filter UI (range sliders for
 * numeric, value drop-downs for strings).
 */
public abstract class AttrStats {
    /** Frequency-table size cap on string attributes: high-cardinality
     *  strings (e.g. unique identifiers like OBJECTID / gml:id) would
     *  otherwise blow up to the full feature count. The first
     *  {@code MAX_TRACKED_VALUES} unique strings encountered are
     *  counted; values seen later are dropped from the frequency map
     *  but still contribute to {@code totalValuesCount}. */
    public static final int MAX_TRACKED_VALUES = 256;

    public abstract void update(Object value);

    public abstract Result toResult();

    public static AttrStats forNumeric() {
        return new Numeric();
    }

    public static AttrStats forString() {
        return new StringFreq();
    }

    /**
     * Snapshot of accumulated statistics, ready for JSON serialization.
     * One of {@link #numericCount} / {@link #frequency} is non-null
     * depending on the attribute type.
     */
    public record Result(long numericCount, double min, double max, double avg,
                         double stddev, double sum, double variance,
                         List<FrequencyEntry> frequency, long totalValuesCount) {
        public boolean isNumeric() {
            return frequency == null;
        }
    }

    public record FrequencyEntry(String value, long count) {
    }

    private static final class Numeric extends AttrStats {
        private long count;
        private double sum;
        private double sumOfSquares;
        private double min = Double.POSITIVE_INFINITY;
        private double max = Double.NEGATIVE_INFINITY;

        @Override
        public synchronized void update(Object value) {
            if (!(value instanceof Number n)) return;
            double v = n.doubleValue();
            if (Double.isNaN(v) || Double.isInfinite(v)) return;
            count++;
            sum += v;
            sumOfSquares += v * v;
            if (v < min) min = v;
            if (v > max) max = v;
        }

        @Override
        public synchronized Result toResult() {
            if (count == 0) {
                return new Result(0, 0, 0, 0, 0, 0, 0, null, 0);
            }
            double avg = sum / count;
            // Variance using Welford-equivalent identity (E[X²] - E[X]²)
            // is fine here: counts/sums are bounded and pre-aggregated, so
            // catastrophic cancellation isn't a real risk on attribute
            // distributions in city models. Clamp tiny negatives from
            // float rounding to 0.
            double variance = (sumOfSquares - sum * sum / count) / count;
            if (variance < 0) variance = 0;
            double stddev = Math.sqrt(variance);
            return new Result(count, min, max, avg, stddev, sum, variance, null, count);
        }
    }

    private static final class StringFreq extends AttrStats {
        private long totalCount;
        private final Map<String, Long> frequency = new HashMap<>();

        @Override
        public synchronized void update(Object value) {
            totalCount++;
            if (value == null) return;
            String s = value.toString();
            // Only count values within the cap, OR an existing key (so we
            // keep updating frequencies for the top-K we've already seen).
            if (frequency.size() < MAX_TRACKED_VALUES || frequency.containsKey(s)) {
                frequency.merge(s, 1L, Long::sum);
            }
        }

        @Override
        public synchronized Result toResult() {
            List<FrequencyEntry> entries = new ArrayList<>(frequency.size());
            frequency.forEach((k, v) -> entries.add(new FrequencyEntry(k, v)));
            // Sort by count desc, then by value asc for deterministic output
            // when counts tie.
            entries.sort(Comparator
                    .comparingLong((FrequencyEntry e) -> e.count()).reversed()
                    .thenComparing(FrequencyEntry::value));
            return new Result(0, 0, 0, 0, 0, 0, 0, entries, totalCount);
        }
    }
}
