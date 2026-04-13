/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.model.i3s;

import com.alibaba.fastjson2.annotation.JSONType;
import org.citydb.vis.model.AttrField;

@JSONType(alphabetic = false)
public record Field(String name, String type, String alias) {
    public static Field of(AttrField field) {
        String esriType = switch (field.type()) {
            case INT -> "esriFieldTypeInteger";
            case DOUBLE -> "esriFieldTypeDouble";
            case STRING -> "esriFieldTypeString";
        };
        return new Field(field.name(), esriType, field.name());
    }
}
