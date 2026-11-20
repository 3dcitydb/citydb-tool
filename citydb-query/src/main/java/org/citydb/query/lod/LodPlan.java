/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.lod;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class LodPlan {
    private static final LodPlan EMPTY = new LodPlan(Map.of());
    private final Map<Set<String>, LodSearch> searches;

    private LodPlan(Map<Set<String>, LodSearch> searches) {
        this.searches = searches;
    }

    public static LodPlan empty() {
        return EMPTY;
    }

    public static LodPlan of(Map<Set<String>, LodSearch> searches) {
        Map<Set<String>, LodSearch> copy = new HashMap<>();
        searches.forEach((group, search) -> {
            if (group != null && search != null) {
                copy.put(Set.copyOf(group), search);
            }
        });

        return new LodPlan(Map.copyOf(copy));
    }

    public static LodPlan of(LodFilter lodFilter, LodSearch search) {
        return of(lodFilter.getGroups().stream()
                .collect(Collectors.toMap(group -> group, group -> search, (a, b) -> a)));
    }

    public LodSearch get(Set<String> group) {
        return searches.getOrDefault(group, LodSearch.TOP_DOWN);
    }

    public Map<Set<String>, LodSearch> getSearches() {
        return searches;
    }

    @Override
    public String toString() {
        return searches.entrySet().stream()
                .map(entry -> (entry.getKey().isEmpty() ? "any LoD" : "LoD " + String.join(", ", entry.getKey()))
                        + ": " + entry.getValue())
                .collect(Collectors.joining("; "));
    }
}
