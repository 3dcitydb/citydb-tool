package org.citydb.web.config.feature;

import java.util.ArrayList;
import java.util.List;

public class FeatureTypes {
    private List<FeatureType> items;

    public FeatureTypes() {
        items = new ArrayList<>();
    }

    public List<FeatureType> getItems() {
        return items;
    }

    public void setItems(List<FeatureType> items) {
        this.items = items;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }
}
