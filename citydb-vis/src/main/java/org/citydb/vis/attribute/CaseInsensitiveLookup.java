/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.attribute;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Case-insensitive name → constant index shared by the projection-grammar
 * enums ({@link FeatureField}, {@link AddressField}, {@link ValueType}).
 * A TreeMap with {@code String.CASE_INSENSITIVE_ORDER} makes lookup ignore
 * case (e.g. {@code uri} / {@code URI} / {@code Uri} all resolve) while
 * preserving the canonical camelCase strings as keys, so {@link #names()} /
 * "Allowed: ..." error hints display the official spelling.
 */
final class CaseInsensitiveLookup<E> {
    private final Map<String, E> byName;

    private CaseInsensitiveLookup(Map<String, E> byName) {
        this.byName = byName;
    }

    static <E> CaseInsensitiveLookup<E> of(E[] values, Function<E, String> nameOf) {
        Map<String, E> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (E value : values) {
            map.put(nameOf.apply(value), value);
        }
        return new CaseInsensitiveLookup<>(Collections.unmodifiableMap(map));
    }

    /** Resolve a user-facing name (case-insensitive), or null if unknown. */
    E get(String name) {
        return name == null ? null : byName.get(name);
    }

    /** Snapshot of every canonical name, for the parser's "Allowed: ..." error hint. */
    Set<String> names() {
        return byName.keySet();
    }
}
