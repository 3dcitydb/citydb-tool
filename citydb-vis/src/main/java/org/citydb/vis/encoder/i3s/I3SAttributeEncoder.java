/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.encoder.i3s;

import org.citydb.vis.encoder.AttrValueCoercer;
import org.citydb.vis.encoder.AttributeEncoder;
import org.citydb.vis.model.AttrField;
import org.citydb.vis.model.FeatureData;
import org.citydb.vis.scene.SceneNode;
import org.citydb.vis.util.BufferUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * I3S-specific binary attribute encoding.
 * <p>
 * Extends the format-agnostic {@link AttributeEncoder} with I3S binary
 * attribute buffer writing (per-node {@code attributes/f_N/0} files).
 */
public class I3SAttributeEncoder extends AttributeEncoder {

    /**
     * Write binary attribute data for all fields of a single node.
     */
    public void writeNodeAttributes(Path layerDir, SceneNode node, List<AttrField> attrFields,
                                    List<FeatureData> features) throws IOException {
        if (features == null || features.isEmpty()) {
            return;
        }

        int count = features.size();
        Path nodeDir = layerDir.resolve("nodes").resolve(String.valueOf(node.getIndex()));
        Path attributesDir = nodeDir.resolve("attributes");
        for (int i = 0; i < attrFields.size(); i++) {
            Files.createDirectories(attributesDir.resolve("f_" + i));
        }

        for (int i = 0; i < attrFields.size(); i++) {
            AttrField field = attrFields.get(i);
            ByteBuffer buffer = switch (field.type()) {
                // OID: Oid32 / INT: Int32 — ArcGIS requires unique non-null
                // OIDs to enable single-feature identify/picking.
                case OID, INT -> writeInt32Attribute(count,
                        AttrValueCoercer.extractInts(features, field.name()));
                case DOUBLE -> writeDoubleAttribute(count,
                        AttrValueCoercer.extractDoubles(features, field.name()));
                case STRING -> writeStringAttribute(count,
                        AttrValueCoercer.extractUtf8(features, field.name()));
            };
            Files.write(attributesDir.resolve("f_" + i).resolve("0"), buffer.array());
        }
    }

    private static ByteBuffer writeInt32Attribute(int count, int[] values) {
        ByteBuffer buffer = BufferUtils.allocateLittleEndian(4 + count * 4);
        buffer.putInt(count);
        for (int v : values) buffer.putInt(v);
        return buffer;
    }

    private static ByteBuffer writeDoubleAttribute(int count, double[] values) {
        // CesiumJS aligns the body to the value type size:
        // Math.ceil(headerSize / 8) * 8 = 8, so 4 bytes of padding are needed
        // after the 4-byte header for Float64 alignment.
        ByteBuffer buffer = BufferUtils.allocateLittleEndian(8 + count * 8);
        buffer.putInt(count);
        buffer.putInt(0);
        for (double v : values) buffer.putDouble(v);
        return buffer;
    }

    private static ByteBuffer writeStringAttribute(int count, byte[][] utf8) {
        // Esri's I3S string attribute format stores each value as UTF-8 bytes
        // PLUS a trailing NUL terminator, and the per-entry byteCount includes
        // that NUL. ArcGIS Pro's String parser fails silently when the
        // terminator is missing, suppressing the whole identify popup.
        int totalBytes = 0;
        for (byte[] b : utf8) totalBytes += b.length + 1;

        ByteBuffer buffer = BufferUtils.allocateLittleEndian(8 + count * 4 + totalBytes);
        buffer.putInt(count);
        buffer.putInt(totalBytes);
        for (byte[] b : utf8) buffer.putInt(b.length + 1);
        for (byte[] b : utf8) {
            buffer.put(b);
            buffer.put((byte) 0);
        }
        return buffer;
    }
}
