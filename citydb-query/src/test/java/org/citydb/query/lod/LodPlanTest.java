/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.lod;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LodPlanTest {

    @Test
    void searchesEachLodSeparatelyInAndMode() {
        LodFilter lodFilter = new LodFilter().withLod(2).withLod(3).setMode(LodFilterMode.AND);
        assertEquals(List.of(Set.of("2"), Set.of("3")), lodFilter.getGroups());
    }

    @Test
    void searchesAllLodsTogetherInOrMode() {
        LodFilter lodFilter = new LodFilter().withLod(2).withLod(3).setMode(LodFilterMode.OR);
        assertEquals(List.of(Set.of("2", "3")), lodFilter.getGroups());
        assertEquals(List.of(Set.of("2", "3")), new LodFilter().withLod(2).withLod(3).getGroups());
    }

    @Test
    void searchesAnyLodWithoutLods() {
        assertEquals(List.of(Set.of()), new LodFilter().setMode(LodFilterMode.AND).getGroups());
    }

    @Test
    void searchesTopDownWithoutEntry() {
        LodPlan plan = LodPlan.of(Map.of(Set.of("3"), LodSearch.BOTTOM_UP));
        assertEquals(LodSearch.BOTTOM_UP, plan.get(Set.of("3")));
        assertEquals(LodSearch.TOP_DOWN, plan.get(Set.of("2")));
        assertEquals(LodSearch.TOP_DOWN, LodPlan.empty().get(Set.of("3")));
    }

    @Test
    void createsPlanForAllGroups() {
        LodFilter lodFilter = new LodFilter().withLod(2).withLod(3).setMode(LodFilterMode.AND);
        LodPlan plan = LodPlan.of(lodFilter, LodSearch.BOTTOM_UP);
        assertEquals(LodSearch.BOTTOM_UP, plan.get(Set.of("2")));
        assertEquals(LodSearch.BOTTOM_UP, plan.get(Set.of("3")));
        assertEquals(LodSearch.TOP_DOWN, plan.get(Set.of("2", "3")));
    }

    @Test
    void ignoresIncompleteEntries() {
        Map<Set<String>, LodSearch> searches = new java.util.HashMap<>();
        searches.put(Set.of("2"), null);
        assertEquals(LodSearch.TOP_DOWN, LodPlan.of(searches).get(Set.of("2")));
    }
}
