package org.citydb.web.config.feature;

import java.util.ArrayList;
import java.util.List;

public class FeatureTypes {
    private List<FeatureType> featureTypes;

    public FeatureTypes() {
        featureTypes = new ArrayList<>();
    }

    public List<FeatureType> getFeatureTypes() {
        return featureTypes;
    }

    public void setFeatureTypes(List<FeatureType> featureTypes) {
        this.featureTypes = featureTypes;
    }
}
