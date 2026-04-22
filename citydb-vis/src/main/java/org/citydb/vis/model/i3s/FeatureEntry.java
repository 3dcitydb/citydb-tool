/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.model.i3s;

import com.alibaba.fastjson2.annotation.JSONType;

/**
 * Single entry of a node's {@code features/0/index.json} {@code featureData}
 * array per the I3S 1.7 spec. The {@code id} is the integer feature index
 * carried by the Draco {@code feature-index} attribute; the ArcGIS Maps SDK
 * for JavaScript uses this table to map a picked triangle back to a feature.
 * <p>
 * {@code mbb} is the exact axis-aligned bounding box of the feature's
 * triangles (computed by
 * {@link org.citydb.vis.encoder.i3s.I3SGeometryEncoder}) and
 * {@code position} is its centroid — the SDK uses both to build the
 * per-node pick BVH; a shared node-level bbox collapses the BVH and
 * makes picks intermittently miss under oblique camera angles.
 */
@JSONType(alphabetic = false)
public record FeatureEntry(long id, double[] position, double[] mbb) {
}
