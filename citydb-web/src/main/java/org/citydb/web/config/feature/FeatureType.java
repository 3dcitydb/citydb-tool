package org.citydb.web.config.feature;

import org.citydb.model.common.Name;

import java.util.Locale;

public class FeatureType {
    private String id;
    private Name name;

    public FeatureType(Name name) {
        this.name = name;
        this.id = name.getLocalName().toLowerCase(Locale.ROOT);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Name getName() {
        return name;
    }

    public void setName(Name name) {
        this.name = name;
    }
}
