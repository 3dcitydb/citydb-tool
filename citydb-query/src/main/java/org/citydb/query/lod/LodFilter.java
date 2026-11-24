/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.lod;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class LodFilter {
    private Set<String> lods;
    private LodFilterMode mode;

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

    @JsonIgnore
    public List<Set<String>> getGroups() {
        Set<String> lods = getLods();
        return mode == LodFilterMode.AND && lods.size() > 1
                ? lods.stream().map(Set::of).toList()
                : List.of(Set.copyOf(lods));
    }
}
