/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.exporter.options;

import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.annotation.JSONField;

import java.util.LinkedHashSet;
import java.util.Set;

public class LodOptions {
    private Set<String> lods;
    @JSONField(serializeFeatures = JSONWriter.Feature.WriteEnumUsingToString)
    private LodMode mode;

    public boolean hasLods() {
        return lods != null && !lods.isEmpty();
    }

    public Set<String> getLods() {
        if (lods == null) {
            lods = new LinkedHashSet<>();
        }

        return lods;
    }

    public LodOptions setLods(Set<String> lods) {
        this.lods = lods;
        return this;
    }

    public LodOptions withLod(String lod) {
        if (lod != null) {
            getLods().add(lod);
        }

        return this;
    }

    public LodOptions withLod(int lod) {
        return withLod(String.valueOf(lod));
    }

    public LodMode getMode() {
        return mode != null ? mode : LodMode.KEEP;
    }

    public LodOptions setMode(LodMode mode) {
        this.mode = mode;
        return this;
    }
}
