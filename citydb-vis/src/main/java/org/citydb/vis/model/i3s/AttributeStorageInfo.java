/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.model.i3s;

import org.citydb.vis.model.AttrField;
import org.citydb.vis.model.AttrType;

import com.alibaba.fastjson2.annotation.JSONType;

import java.util.List;

@JSONType(alphabetic = false)
public class AttributeStorageInfo {
    private String key;
    private String name;
    private List<HeaderEntry> header;
    private List<String> ordering;
    private ValueInfo attributeByteCounts;
    private ValueInfo attributeValues;

    public static AttributeStorageInfo of(int index, AttrField field) {
        AttributeStorageInfo info = new AttributeStorageInfo();
        info.key = "f_" + index;
        info.name = field.name();

        if (field.type() == AttrType.STRING) {
            info.header = List.of(
                    new HeaderEntry("count", "UInt32"),
                    new HeaderEntry("attributeValuesByteCount", "UInt32"));
            info.ordering = List.of("attributeByteCounts", "attributeValues");
            info.attributeByteCounts = ValueInfo.of("UInt32", 1);
            info.attributeValues = ValueInfo.string();
        } else {
            String valueType = switch (field.type()) {
                case OID -> "Oid32";
                case INT -> "Int32";
                case DOUBLE -> "Float64";
                default -> throw new IllegalStateException("Unexpected type: " + field.type());
            };
            info.header = List.of(new HeaderEntry("count", "UInt32"));
            info.ordering = List.of("attributeValues");
            info.attributeValues = ValueInfo.of(valueType, 1);
        }

        return info;
    }

    @JSONType(alphabetic = false)
    public record HeaderEntry(String property, String valueType) {
    }

    @JSONType(alphabetic = false)
    public static class ValueInfo {
        private String valueType;
        private int valuesPerElement;
        private String encoding;

        public static ValueInfo of(String valueType, int valuesPerElement) {
            ValueInfo info = new ValueInfo();
            info.valueType = valueType;
            info.valuesPerElement = valuesPerElement;
            return info;
        }

        public static ValueInfo string() {
            ValueInfo info = of("String", 1);
            info.encoding = "UTF-8";
            return info;
        }
    }
}
