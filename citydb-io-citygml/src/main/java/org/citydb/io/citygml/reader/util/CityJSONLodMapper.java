/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.reader.util;

import org.citygml4j.cityjson.util.lod.LodMapper;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class CityJSONLodMapper implements LodMapper {
    private final Map<Integer, Double> mappings;
    private final LodMapper fallback;
    private Set<Double> lods;

    public CityJSONLodMapper(Map<Integer, Double> mappings, LodMapper fallback) {
        this.mappings = Objects.requireNonNull(mappings, "The LoD mappings must not be null.");
        this.fallback = Objects.requireNonNull(fallback, "The fallback LoD mapper must not be null.");
    }

    @Override
    public void buildMapping(Set<Double> lods) {
        this.lods = lods;
        fallback.buildMapping(lods);
    }

    @Override
    public double getMappingFor(int lod) {
        Double mapping = mappings.get(lod);
        return mapping != null && lods != null && lods.contains(mapping)
                ? mapping
                : fallback.getMappingFor(lod);
    }
}
