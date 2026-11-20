/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.util;

import java.util.Map;
import java.util.Set;

/**
 * Planner statistics of the LoDs of the geometry properties, from which the number of rows with given LoDs is
 * estimated as the sum of their frequencies times the number of rows.
 * <p>
 * Adapters must provide the values as follows, independent of how their database stores them:
 * <ul>
 *     <li>The frequencies are shares of all rows, including the rows without LoD. The null fraction is the share
 *     of rows without LoD, also relative to all rows.</li>
 *     <li>Every LoD that is not rare must be listed. Unlisted LoDs are estimated as 0, so only LoDs too rare to
 *     appear in the sample of the database may be omitted.</li>
 *     <li>The statistics are marked as stale if they may no longer reflect the data.</li>
 * </ul>
 * An adapter that cannot guarantee this, e.g. because its histogram may omit frequent values, must not provide
 * statistics.
 */
public class LodStatistics {
    private final Map<String, Double> frequencies;
    private final double nullFraction;
    private final long rows;
    private final boolean stale;

    private LodStatistics(Map<String, Double> frequencies, double nullFraction, long rows, boolean stale) {
        this.frequencies = Map.copyOf(frequencies);
        this.nullFraction = nullFraction;
        this.rows = rows;
        this.stale = stale;
    }

    public static LodStatistics of(Map<String, Double> frequencies, double nullFraction, long rows, boolean stale) {
        return new LodStatistics(frequencies, nullFraction, rows, stale);
    }

    public Map<String, Double> getFrequencies() {
        return frequencies;
    }

    public double getNullFraction() {
        return nullFraction;
    }

    public long getRows() {
        return rows;
    }

    public boolean isStale() {
        return stale;
    }

    public long estimate(Set<String> lods) {
        double fraction = lods.isEmpty()
                ? 1 - nullFraction
                : lods.stream().mapToDouble(lod -> frequencies.getOrDefault(lod, 0d)).sum();
        return Math.round(fraction * rows);
    }
}
