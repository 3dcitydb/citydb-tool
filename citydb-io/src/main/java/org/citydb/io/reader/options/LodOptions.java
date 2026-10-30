/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.reader.options;

import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.annotation.JSONField;

import java.util.LinkedHashSet;
import java.util.Set;

public class LodOptions {
    private Set<Integer> lods;
    @JSONField(serializeFeatures = JSONWriter.Feature.WriteEnumUsingToString)
    private LodMode mode;
    @JSONField(serializeFeatures = JSONWriter.Feature.WriteEnumUsingToString)
    private EmptyFeatureMode emptyFeatures;
    private boolean updateExtents = true;

    public boolean hasLods() {
        return lods != null && !lods.isEmpty();
    }

    public Set<Integer> getLods() {
        if (lods == null) {
            lods = new LinkedHashSet<>();
        }

        return lods;
    }

    public LodOptions setLods(Set<Integer> lods) {
        this.lods = lods;
        return this;
    }

    public LodOptions withLod(int lod) {
        getLods().add(lod);
        return this;
    }

    public LodMode getMode() {
        return mode != null ? mode : LodMode.KEEP;
    }

    public LodOptions setMode(LodMode mode) {
        this.mode = mode;
        return this;
    }

    public EmptyFeatureMode getEmptyFeatures() {
        return emptyFeatures != null ? emptyFeatures : EmptyFeatureMode.REMOVE;
    }

    public LodOptions setEmptyFeatures(EmptyFeatureMode emptyFeatures) {
        this.emptyFeatures = emptyFeatures;
        return this;
    }

    public boolean isUpdateExtents() {
        return updateExtents;
    }

    public LodOptions setUpdateExtents(boolean updateExtents) {
        this.updateExtents = updateExtents;
        return this;
    }
}
