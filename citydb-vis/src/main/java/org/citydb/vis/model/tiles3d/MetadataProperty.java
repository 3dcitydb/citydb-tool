/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.model.tiles3d;

import com.alibaba.fastjson2.annotation.JSONType;
import org.citydb.vis.model.AttrType;

@JSONType(alphabetic = false)
public record MetadataProperty(String type) {

    /**
     * Map a format-agnostic {@link AttrType} to its 3D Tiles 1.1 type string.
     */
    public static String tilesType(AttrType attrType) {
        return switch (attrType) {
            case OID, INT -> "INT32";
            case DOUBLE -> "FLOAT64";
            case STRING -> "STRING";
        };
    }
}
