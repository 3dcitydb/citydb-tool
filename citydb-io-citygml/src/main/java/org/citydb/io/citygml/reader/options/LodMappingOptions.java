/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.reader.options;

import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.annotation.JSONField;

import java.util.Map;
import java.util.TreeMap;

public class LodMappingOptions {
    @JSONField(serializeFeatures = JSONWriter.Feature.WriteEnumUsingToString)
    private LodMappingStrategy strategy;
    private Map<Integer, String> mappings;

    public LodMappingStrategy getStrategy() {
        return strategy != null ? strategy : LodMappingStrategy.MAXIMUM;
    }

    public LodMappingOptions setStrategy(LodMappingStrategy strategy) {
        this.strategy = strategy;
        return this;
    }

    public boolean hasMappings() {
        return mappings != null && !mappings.isEmpty();
    }

    public Map<Integer, String> getMappings() {
        if (mappings == null) {
            mappings = new TreeMap<>();
        }

        return mappings;
    }

    public LodMappingOptions setMappings(Map<Integer, String> mappings) {
        this.mappings = mappings;
        return this;
    }

    public LodMappingOptions withMapping(int lod, String cityJSONLod) {
        getMappings().put(lod, cityJSONLod);
        return this;
    }
}
