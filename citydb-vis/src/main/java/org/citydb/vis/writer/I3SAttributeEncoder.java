/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.writer;

import org.citydb.model.feature.Feature;
import org.citydb.model.property.Attribute;
import org.citydb.model.property.Property;
import org.citydb.vis.scene.I3SNode;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Handles I3S attribute type detection and binary attribute encoding.
 * <p>
 * Call {@link #trackFieldTypes} during the write phase, then
 * {@link #finalizeFields} during close. Thread-safe — uses concurrent
 * data structures.
 */
class I3SAttributeEncoder {

    enum AttrType { STRING, INT, DOUBLE }

    record AttrField(String name, AttrType type) {}

    // --- Incremental type tracking (thread-safe) ---

    private final ConcurrentHashMap<String, AttrType> trackedTypes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> trackedCounts = new ConcurrentHashMap<>();

    /**
     * Track attribute field types incrementally during the write phase.
     * Thread-safe — can be called concurrently from multiple writer threads.
     */
    void trackFieldTypes(Map<String, Object> attributes) {
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            String name = entry.getKey();
            AttrType valueType = classifyType(entry.getValue());
            trackedTypes.merge(name, valueType, this::promoteType);
            trackedCounts.computeIfAbsent(name, k -> new AtomicLong(0)).incrementAndGet();
        }
    }

    /**
     * Finalize attribute fields from incrementally tracked types.
     * Must be called after all features have been processed.
     *
     * @param totalFeatures total number of features processed
     * @return ordered list of attribute fields with final types
     */
    List<AttrField> finalizeFields(long totalFeatures) {
        Map<String, AttrType> result = new LinkedHashMap<>();
        result.put("OBJECTID", AttrType.STRING);
        result.put("featureType", AttrType.STRING);

        trackedTypes.forEach((name, type) -> {
            AttrType finalType = type;
            if (finalType == AttrType.INT) {
                long count = trackedCounts.getOrDefault(name, new AtomicLong(0)).get();
                if (count < totalFeatures) {
                    finalType = AttrType.DOUBLE; // promote: NaN represents missing values
                }
            }
            result.put(name, finalType);
        });

        List<AttrField> fields = new ArrayList<>();
        result.forEach((name, type) -> fields.add(new AttrField(name, type)));
        return fields;
    }

    // --- Attribute extraction ---

    /**
     * Extract typed attribute values from a Feature.
     */
    Map<String, Object> extractAttributes(Feature feature) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (feature.hasAttributes()) {
            for (Attribute attr : feature.getAttributes().getAll()) {
                String name = attr.getName().getLocalName();
                Object value = getAttributeValue(attr);
                if (value != null) {
                    result.put(name, value);
                }
            }
        }
        return result;
    }

    // --- Binary encoding ---

    /**
     * Write binary attribute data for all fields of a single node.
     * Accepts the feature list directly (no map lookup).
     */
    void writeNodeAttributes(Path layerDir, I3SNode node, List<AttrField> attrFields,
                             List<FeatureData> features) throws IOException {
        if (features == null || features.isEmpty()) {
            return;
        }

        int count = features.size();
        Path nodeDir = layerDir.resolve("nodes").resolve(String.valueOf(node.getIndex()));

        for (int i = 0; i < attrFields.size(); i++) {
            AttrField field = attrFields.get(i);
            ByteBuffer buffer;

            switch (field.type()) {
                case INT -> {
                    buffer = ByteBuffer.allocate(4 + count * 4);
                    buffer.order(ByteOrder.LITTLE_ENDIAN);
                    buffer.putInt(count);
                    for (FeatureData fd : features) {
                        Object val = getFieldValue(fd, field.name());
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
                        Object val = getFieldValue(fd, field.name());
                        double dVal;
                        if (val instanceof Double d) dVal = d;
                        else if (val instanceof Long l) dVal = l.doubleValue();
                        else dVal = Double.NaN;
                        buffer.putDouble(dVal);
                    }
                }
                case STRING -> {
                    List<byte[]> valueBytes = new ArrayList<>();
                    for (FeatureData fd : features) {
                        Object val = getFieldValue(fd, field.name());
                        String str = val != null ? val.toString() : "";
                        valueBytes.add(str.getBytes(StandardCharsets.UTF_8));
                    }
                    int totalBytes = 0;
                    for (byte[] b : valueBytes) totalBytes += b.length;

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

    // --- Internal helpers ---

    private AttrType classifyType(Object value) {
        if (value instanceof Long) return AttrType.INT;
        if (value instanceof Double) return AttrType.DOUBLE;
        return AttrType.STRING;
    }

    private Object getFieldValue(FeatureData fd, String fieldName) {
        if ("OBJECTID".equals(fieldName)) return fd.objectId();
        if ("featureType".equals(fieldName)) return fd.featureType();
        return fd.attributes().get(fieldName);
    }

    private Object getAttributeValue(Attribute attr) {
        if (attr.getIntValue().isPresent()) {
            return attr.getIntValue().get();
        }
        if (attr.getDoubleValue().isPresent()) {
            return attr.getDoubleValue().get();
        }
        if (attr.getStringValue().isPresent()) {
            return attr.getStringValue().get();
        }
        if (attr.getTimeStamp().isPresent()) {
            return attr.getTimeStamp().get().toString();
        }
        if (attr.getURI().isPresent()) {
            return attr.getURI().get();
        }
        if (attr.hasProperties()) {
            for (Property<?> prop : attr.getProperties().getAll()) {
                if (prop instanceof Attribute sub && "value".equals(sub.getName().getLocalName())) {
                    Object val = getAttributeValue(sub);
                    if (val != null) {
                        return val;
                    }
                }
            }
        }
        return null;
    }

    private AttrType promoteType(AttrType a, AttrType b) {
        if (a == b) return a;
        if (a == AttrType.STRING || b == AttrType.STRING) return AttrType.STRING;
        return AttrType.DOUBLE;
    }
}
