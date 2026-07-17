/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.model;

/**
 * One placed instance of a shared implicit-geometry prototype within a node,
 * ready for GPU-instancing encoding.
 *
 * @param anchor     anchor position {@code [lon°, lat°, z m]} (EPSG:4326)
 * @param rotation   unit quaternion {@code [x, y, z, w]}, prototype-local → ENU
 *                   at the anchor
 * @param scale      per-axis scale {@code [sx, sy, sz]}
 * @param styleColor sRGB RGBA of the parent feature type's resolved style,
 *                   captured at ingest; drives the instanced node's plain
 *                   material (and its grouping — instances of one prototype
 *                   with different styles land in different glTF nodes)
 * @param feature    attribute payload for the metadata property table (also
 *                   carries the instance's featureId)
 */
public record InstancedFeature(double[] anchor, float[] rotation, float[] scale,
                               float[] styleColor, FeatureData feature) {
}
