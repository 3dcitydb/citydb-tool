/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.model.tiles3d;

import com.alibaba.fastjson2.annotation.JSONType;

@JSONType(alphabetic = false)
public record TilesetAsset(String version, String generator) {
    static final TilesetAsset TILES_3D = new TilesetAsset("1.1", "3DCityDB citydb-tool");
}
