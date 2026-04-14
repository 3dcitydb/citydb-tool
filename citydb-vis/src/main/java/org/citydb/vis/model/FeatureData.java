/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.model;

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
}
