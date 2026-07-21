/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.encoder.tiles3d;

import org.citydb.vis.attribute.AttributeValueCoercer;
import org.citydb.vis.model.AttrField;
import org.citydb.vis.model.FeatureData;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Encodes one {@code EXT_structural_metadata} property-table column into BIN
 * buffer views: INT32 / FLOAT64 columns as a single values view, STRING
 * columns as a UTF-8 values view plus a string-offsets view. Value coercion
 * per {@link org.citydb.vis.model.AttrType} is delegated to the shared
 * {@link AttributeValueCoercer} so both writers coerce identically.
 */
final class PropertyTableEncoder {
    private PropertyTableEncoder() {
    }

    static PropertyTableBufferViews encode(
            BinBufferBuilder bin, AttrField field, List<FeatureData> features) {
        return AttributeValueCoercer.dispatchByType(field.type(), features, field.name(),
                v -> new PropertyTableBufferViews(bin.addInt32Array(v), -1),
                v -> new PropertyTableBufferViews(bin.addFloat64Array(v), -1),
                v -> encodeStringProperty(bin, v));
    }

    private static PropertyTableBufferViews encodeStringProperty(
            BinBufferBuilder bin, byte[][] utf8) {
        ByteArrayOutputStream valuesStream = new ByteArrayOutputStream();
        int[] offsets = new int[utf8.length + 1];
        int offset = 0;
        for (int i = 0; i < utf8.length; i++) {
            offsets[i] = offset;
            valuesStream.writeBytes(utf8[i]);
            offset += utf8[i].length;
        }
        offsets[utf8.length] = offset;

        // glTF rejects bufferView.byteLength == 0, while EXT_structural_metadata
        // requires the values bufferView byteLength to equal the last string
        // offset. When every value is empty there's no way to satisfy both —
        // skip the property entirely. The schema keeps the column (other GLBs
        // in the same tileset may populate it); 3D Tiles 1.1 lets a property
        // table omit non-required properties.
        if (valuesStream.size() == 0) {
            return PropertyTableBufferViews.SKIPPED;
        }
        int valuesBv = bin.addRawBytes(valuesStream.toByteArray());
        int offsetsBv = bin.addInt32Array(offsets);
        return new PropertyTableBufferViews(valuesBv, offsetsBv);
    }
}
