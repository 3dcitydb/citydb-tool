/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.model.i3s;

import com.alibaba.fastjson2.annotation.JSONType;

/**
 * I3S node-page mesh material reference. {@code texelCountHint} is required
 * by ArcGIS Pro on textured materials; pass {@code null} for untextured
 * nodes — fastjson2 omits the field when serializing a null component.
 */
@JSONType(alphabetic = false)
public record MeshMaterial(int definition, int resource, Integer texelCountHint) {
}
