/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight metadata for a processed city model feature, used during
 * the close phase for per-node feature/attribute output.
 */
public record FeatureData(long id, String objectId, String featureType,
                          Map<String, Object> attributes) {

    /**
     * Retrieve a field value by name. Handles the fixed header fields
     * (OBJECTID, featureType) and delegates to the user attribute map
     * for everything else.
     */
    public Object getFieldValue(String fieldName) {
        // OID is the Esri integer OID (sequential, for picking).
        // OBJECTID carries the CityGML gml:id string so that the identifier
        // shown in the ArcGIS identify popup matches what Cesium displays.
        if ("OID".equals(fieldName)) return id;
        if ("OBJECTID".equals(fieldName)) return objectId;
        if ("featureType".equals(fieldName)) return featureType;
        return attributes.get(fieldName);
    }

    /**
     * Return a new list with features ordered to match {@code ids}. Used by
     * geometry encoders when degenerate-triangle filtering has removed all
     * faces for some features, so the per-feature property table must be
     * reduced and reordered to align with the surviving face-range order.
     * Entries whose id is absent from the input are mapped to {@code null}.
     */
    public static List<FeatureData> reorderByIds(List<FeatureData> features, List<Long> ids) {
        Map<Long, FeatureData> byId = new HashMap<>(features.size() * 2);
        for (FeatureData fd : features) {
            byId.put(fd.id(), fd);
        }
        List<FeatureData> result = new ArrayList<>(ids.size());
        for (long id : ids) {
            result.add(byId.get(id));
        }
        return result;
    }
}
