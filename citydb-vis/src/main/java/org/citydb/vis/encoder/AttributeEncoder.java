/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.encoder;

import org.citydb.model.feature.Feature;
import org.citydb.model.property.Attribute;
import org.citydb.model.property.Property;
import org.citydb.vis.model.AttrField;
import org.citydb.vis.model.AttrType;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Format-agnostic attribute type detection and extraction.
 * <p>
 * Call {@link #trackFieldTypes} during the write phase, then
 * {@link #finalizeFields} during close. Thread-safe — uses concurrent
 * data structures.
 * <p>
 * Subclasses add format-specific binary encoding (e.g., I3S binary
 * attribute buffers, 3D Tiles batch table / EXT_structural_metadata).
 */
public class AttributeEncoder {

    // ---- Incremental type tracking (thread-safe) ----

    private final ConcurrentHashMap<String, AttrType> trackedTypes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> trackedCounts = new ConcurrentHashMap<>();

    /**
     * Track attribute field types incrementally during the write phase.
     * Thread-safe — can be called concurrently from multiple writer threads.
     */
    public void trackFieldTypes(Map<String, Object> attributes) {
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            String name = entry.getKey();
            AttrType valueType = classifyType(entry.getValue());
            trackedTypes.merge(name, valueType, AttributeEncoder::promoteType);
            trackedCounts.computeIfAbsent(name, k -> new AtomicLong(0)).incrementAndGet();
        }
    }

    /**
     * Finalize attribute fields from incrementally tracked types.
     * Must be called after all features have been processed.
     * <p>
     * Fields are ordered deterministically: the two fixed header fields
     * (OBJECTID, featureType) first, then user attributes sorted alphabetically
     * by name. ConcurrentHashMap iteration order is unspecified, so sorting
     * here is necessary for reproducible output across runs.
     *
     * @param totalFeatures total number of features processed
     * @return ordered list of attribute fields with final types
     */
    public List<AttrField> finalizeFields(long totalFeatures) {
        Map<String, AttrType> result = new LinkedHashMap<>();
        // OID: Esri integer OID (esriFieldTypeOID / Oid32). Value =
        // sequential feature counter from VisWriter.featureIdCounter.
        // Used by ArcGIS Pro for single-feature picking.
        result.put("OID", AttrType.OID);
        // OBJECTID: the CityGML gml:id string (e.g., "DEBY_LOD2_4865511").
        // Matches the identifier users see in Cesium's picking popup, so
        // ArcGIS and Cesium both surface the same human-readable value.
        result.put("OBJECTID", AttrType.STRING);
        result.put("featureType", AttrType.STRING);

        // Copy into a TreeMap so iteration order is stable and alphabetical.
        Map<String, AttrType> sorted = new TreeMap<>(trackedTypes);
        sorted.forEach((name, type) -> {
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

    // ---- Attribute extraction ----

    /**
     * Extract typed attribute values from a Feature.
     */
    public Map<String, Object> extractAttributes(Feature feature) {
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

    // ---- Internal helpers ----

    private static AttrType classifyType(Object value) {
        if (value instanceof Long l) {
            // INT maps to Int32. Long values outside that range would be
            // silently truncated, so promote to DOUBLE (Float64 covers all
            // safe-integer longs up to 2^53 exactly).
            return (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE)
                    ? AttrType.INT : AttrType.DOUBLE;
        }
        if (value instanceof Double) return AttrType.DOUBLE;
        return AttrType.STRING;
    }

    private static Object getAttributeValue(Attribute attr) {
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

    private static AttrType promoteType(AttrType a, AttrType b) {
        if (a == b) return a;
        if (a == AttrType.STRING || b == AttrType.STRING) return AttrType.STRING;
        return AttrType.DOUBLE;
    }
}
