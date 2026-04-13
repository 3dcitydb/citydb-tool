/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.model.i3s;

import org.citydb.vis.model.FeatureData;

import com.alibaba.fastjson2.annotation.JSONType;

/**
 * Single entry of a node's features/0/index.json array — the I3S feature
 * metadata record exposed to clients. {@link FeatureData} carries additional
 * per-feature attributes that belong in the binary attribute streams instead.
 */
@JSONType(alphabetic = false)
public record FeatureEntry(long id, String objectId, String featureType) {
    public static FeatureEntry from(FeatureData data) {
        return new FeatureEntry(data.id(), data.objectId(), data.featureType());
    }
}
