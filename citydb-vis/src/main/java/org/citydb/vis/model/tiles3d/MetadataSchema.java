/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.model.tiles3d;

import com.alibaba.fastjson2.annotation.JSONType;
import org.citydb.vis.model.AttrField;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JSONType(alphabetic = false)
public class MetadataSchema {
    private Map<String, MetadataClass> classes;

    public static MetadataSchema of(List<AttrField> attrFields) {
        MetadataSchema schema = new MetadataSchema();

        Map<String, MetadataProperty> properties = new LinkedHashMap<>();
        for (AttrField field : attrFields) {
            String type = switch (field.type()) {
                case INT -> "INT32";
                case DOUBLE -> "FLOAT64";
                case STRING -> "STRING";
            };
            properties.put(field.name(), new MetadataProperty(type));
        }

        schema.classes = new LinkedHashMap<>();
        schema.classes.put("feature", new MetadataClass(properties));
        return schema;
    }
}
