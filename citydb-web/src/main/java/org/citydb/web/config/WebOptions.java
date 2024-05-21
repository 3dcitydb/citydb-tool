package org.citydb.web.config;

import org.citydb.config.SerializableConfig;
import org.citydb.model.feature.FeatureType;
import org.citydb.web.schema.Collection;
import org.citydb.web.schema.Collections;
import org.citydb.web.schema.Link;

import java.util.List;

import static java.util.Collections.singletonList;

@SerializableConfig(name = "webOptions")
public class WebOptions {
    private Collections featureCollections;

    public WebOptions() {
        featureCollections = Collections.of(
                singletonList(Link.of("http://localhost:8080/ogcapi/collections", "items")
                        .setType("application/geo+json")),
                singletonList(Collection.of(FeatureType.BUILDING.getName().getLocalName())
                        .setLinks(List.of(Link.of("http://localhost:8080/ogcapi/collections/building/items", "items")))));
    }

    public Collections getFeatureCollections() {
        return featureCollections;
    }

    public void setFeatureCollections(Collections featureCollections) {
        this.featureCollections = featureCollections;
    }
}
