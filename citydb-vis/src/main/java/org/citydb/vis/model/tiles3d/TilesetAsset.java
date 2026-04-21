/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.model.tiles3d;

import com.alibaba.fastjson2.annotation.JSONType;

@JSONType(alphabetic = false)
public record TilesetAsset(String version, String generator) {
    static final TilesetAsset TILES_3D = new TilesetAsset("1.1", "3DCityDB citydb-tool");
}
