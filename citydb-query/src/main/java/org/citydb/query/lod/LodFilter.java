/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.lod;

import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.annotation.JSONField;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

public class LodFilter {
    private Set<String> lods;
    @JSONField(serializeFeatures = JSONWriter.Feature.WriteEnumUsingToString)
    private LodFilterMode mode;
    private Integer searchDepth;

    public Set<String> getLods() {
        if (lods == null) {
            lods = new LinkedHashSet<>();
        }

        return lods;
    }

    public LodFilter setLods(Set<String> lods) {
        this.lods = lods;
        return this;
    }

    public LodFilter withLod(String lod) {
        if (lod != null) {
            getLods().add(lod);
        }

        return this;
    }

    public LodFilter withLod(int lod) {
        return withLod(String.valueOf(lod));
    }

    public LodFilterMode getMode() {
        return mode;
    }

    public LodFilter setMode(LodFilterMode mode) {
        this.mode = mode != null ? mode : LodFilterMode.OR;
        return this;
    }

    public Optional<Integer> getSearchDepth() {
        return searchDepth != null && searchDepth >= 0 ?
                Optional.of(searchDepth) :
                Optional.empty();
    }

    public LodFilter setSearchDepth(Integer searchDepth) {
        this.searchDepth = searchDepth;
        return this;
    }
}
