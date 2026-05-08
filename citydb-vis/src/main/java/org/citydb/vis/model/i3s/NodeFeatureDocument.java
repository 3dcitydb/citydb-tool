/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.model.i3s;

import com.alibaba.fastjson2.annotation.JSONType;

import java.util.List;

/**
 * Root object of a node's {@code features/0/index.json} document per the
 * I3S 1.7 spec. The earlier writer emitted a bare array of feature entries,
 * which the ArcGIS Maps SDK for JavaScript rejects — without a parseable
 * feature document the SDK cannot register the node's mesh in the pick
 * pipeline, so {@code SceneView.hitTest} returns no features even though
 * the buildings render correctly.
 * <p>
 * The {@code geometryData} array references the layer's per-node
 * geometry buffer file; with the single-buffer layout it always points
 * at {@code ./geometries/0} (the uncompressed legacy I3S 1.7 binary).
 */
@JSONType(alphabetic = false)
public record NodeFeatureDocument(List<FeatureEntry> featureData,
                                  List<GeometryDataRef> geometryData) {

    @JSONType(alphabetic = false)
    public record GeometryDataRef(String href) {
    }

    public static NodeFeatureDocument of(List<FeatureEntry> entries) {
        return new NodeFeatureDocument(entries,
                List.of(new GeometryDataRef("./geometries/0")));
    }
}
