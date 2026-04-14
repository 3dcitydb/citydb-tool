/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.encoder.i3s;

import org.citydb.vis.encoder.AttributeEncoder;

import org.citydb.vis.model.AttrField;
import org.citydb.vis.model.FeatureData;
import org.citydb.vis.scene.SceneNode;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
            ByteBuffer buffer;

            switch (field.type()) {
                case OID -> {
                    // Oid32: sequential feature id (from FeatureData.id, 1-based)
                    // encoded as Int32 LE. ArcGIS requires unique non-null OIDs
                    // to enable single-feature identify/picking.
                    buffer = ByteBuffer.allocate(4 + count * 4);
                    buffer.order(ByteOrder.LITTLE_ENDIAN);
                    buffer.putInt(count);
                    for (FeatureData fd : features) {
                        buffer.putInt((int) fd.id());
                    }
                }
                case INT -> {
                    buffer = ByteBuffer.allocate(4 + count * 4);
                    buffer.order(ByteOrder.LITTLE_ENDIAN);
                    buffer.putInt(count);
                    for (FeatureData fd : features) {
                        Object val = fd.getFieldValue(field.name());
                        int intVal = 0;
                        if (val instanceof Long l) intVal = l.intValue();
                        else if (val instanceof Double d) intVal = d.intValue();
                        buffer.putInt(intVal);
                    }
                }
                case DOUBLE -> {
                    // CesiumJS aligns the body to the value type size:
                    // Math.ceil(headerSize / 8) * 8 = 8, so 4 bytes of padding
                    // are needed after the 4-byte header for Float64 alignment.
                    buffer = ByteBuffer.allocate(8 + count * 8);
                    buffer.order(ByteOrder.LITTLE_ENDIAN);
                    buffer.putInt(count);
                    buffer.putInt(0); // padding for Float64 alignment
                    for (FeatureData fd : features) {
                        Object val = fd.getFieldValue(field.name());
                        double dVal;
                        if (val instanceof Double d) dVal = d;
                        else if (val instanceof Long l) dVal = l.doubleValue();
                        else dVal = Double.NaN;
                        buffer.putDouble(dVal);
                    }
                }
                case STRING -> {
                    // Esri's I3S string attribute format stores each value as
                    // UTF-8 bytes PLUS a trailing NUL terminator, and the
                    // per-entry byteCount includes that NUL. ArcGIS Pro's
                    // String parser fails silently when the terminator is
                    // missing, suppressing the whole identify popup.
                    List<byte[]> valueBytes = new ArrayList<>();
                    int totalBytes = 0;
                    for (FeatureData fd : features) {
                        Object val = fd.getFieldValue(field.name());
                        String str = val != null ? val.toString() : "";
                        byte[] utf8 = str.getBytes(StandardCharsets.UTF_8);
                        byte[] nulTerminated = new byte[utf8.length + 1];
                        System.arraycopy(utf8, 0, nulTerminated, 0, utf8.length);
                        // last byte already 0 (NUL terminator)
                        valueBytes.add(nulTerminated);
                        totalBytes += nulTerminated.length;
                    }

                    buffer = ByteBuffer.allocate(4 + 4 + count * 4 + totalBytes);
                    buffer.order(ByteOrder.LITTLE_ENDIAN);
                    buffer.putInt(count);
                    buffer.putInt(totalBytes);
                    for (byte[] b : valueBytes) buffer.putInt(b.length);
                    for (byte[] b : valueBytes) buffer.put(b);
                }
                default -> throw new IOException("Unknown attribute type: " + field.type());
            }

            Files.write(nodeDir.resolve("attributes").resolve("f_" + i).resolve("0"), buffer.array());
        }
    }
}
