/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.util;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LodStatisticsTest {
    // the statistics of the wien test database
    private final LodStatistics statistics = LodStatistics.of(Map.of("2", 0.0819, "1", 0.009966667),
            0.9081333, 61242916, false);

    @Test
    void estimatesListedLods() {
        assertEquals(Math.round(0.0819 * 61242916), statistics.estimate(Set.of("2")));
        assertEquals(Math.round(0.009966667 * 61242916), statistics.estimate(Set.of("1")));
        assertEquals(Math.round((0.0819 + 0.009966667) * 61242916), statistics.estimate(Set.of("1", "2")));
    }

    @Test
    void countsUnlistedLodsAsZero() {
        assertEquals(0, statistics.estimate(Set.of("3")));
        assertEquals(statistics.estimate(Set.of("1")), statistics.estimate(Set.of("1", "3")));
    }

    @Test
    void estimatesAnyLodWithoutLods() {
        assertEquals(Math.round((1 - 0.9081333) * 61242916), statistics.estimate(Set.of()));
    }

    @Test
    void keepsStaleFlagOfAdapter() {
        assertTrue(LodStatistics.of(Map.of("2", 0.5), 0.5, 1000, true).isStale());
        assertFalse(statistics.isStale());
    }
}
