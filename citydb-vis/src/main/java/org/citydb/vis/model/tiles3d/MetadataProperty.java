/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.model.tiles3d;

import com.alibaba.fastjson2.annotation.JSONType;
import org.citydb.vis.model.AttrType;

@JSONType(alphabetic = false)
public record MetadataProperty(String type, String componentType) {

    /**
     * Map a format-agnostic {@link AttrType} to its 3D Tiles 1.1 metadata
     * property descriptor (type + optional componentType).
     */
    public static MetadataProperty of(AttrType attrType) {
        return switch (attrType) {
            case OID, INT -> new MetadataProperty("SCALAR", "INT32");
            case DOUBLE -> new MetadataProperty("SCALAR", "FLOAT64");
            case STRING -> new MetadataProperty("STRING", null);
        };
    }
}
