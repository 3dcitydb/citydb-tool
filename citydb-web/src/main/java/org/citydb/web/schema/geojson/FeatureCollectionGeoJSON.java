package org.citydb.web.schema.geojson;

import java.util.ArrayList;
import java.util.List;

public class FeatureCollectionGeoJSON {
    private final String type;
    private final List<FeatureGeoJSON> features;

    private FeatureCollectionGeoJSON() {
        this.type = "FeatureCollection";
        this.features = new ArrayList<>();
    }

    public static FeatureCollectionGeoJSON newInstance() {
        return new FeatureCollectionGeoJSON();
    }

    public String getType() {
        return type;
    }

    public void addFeature(FeatureGeoJSON feature) {
        features.add(feature);
    }

    public List<FeatureGeoJSON> getFeatures() {
        return features;
    }
}
