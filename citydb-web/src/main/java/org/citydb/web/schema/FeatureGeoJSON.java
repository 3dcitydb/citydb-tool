package org.citydb.web.schema;

import java.util.HashMap;
import java.util.Map;

public class FeatureGeoJSON {
    private final String type;
    private GeometryGeoJSON geometry;
    private Map<String, Object> properties;

    private FeatureGeoJSON(GeometryGeoJSON geometry) {
        this.type = "Feature";
        this.geometry = geometry;
        this.properties = new HashMap<>();
    }

    public static FeatureGeoJSON of(GeometryGeoJSON geometry) {
        return new FeatureGeoJSON(geometry);
    }

    public String getType() {
        return type;
    }

    public GeometryGeoJSON getGeometry() {
        return geometry;
    }

    public void setGeometry(GeometryGeoJSON geometry) {
        this.geometry = geometry;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }
}
