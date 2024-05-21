package org.citydb.web.service;

import org.citydb.model.feature.FeatureType;
import org.citydb.web.schema.Collection;
import org.citydb.web.schema.Collections;
import org.citydb.web.schema.Link;
import org.citydb.web.util.BboxCalculator;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.singletonList;

public class CollectionService {
    private final BboxCalculator bboxCalculator = new BboxCalculator();

    public Collections getCollections() throws ServiceException {
        List<Link> links = singletonList(
                Link.of("http://localhost:8080/ogcapi/collections/building/items", "items")
                        .setType("application/geo+json")
                        .setTitle("building items")
        );
        try {
            return Collections.of(links, new ArrayList<>(List.of(getCollection())));
        } catch (SQLException e) {
            throw new ServiceException("Failed to create collection list.", e);
        }
    }

    public Collection getCollection(String id) throws ServiceException {
        try {
            return getCollection();
        } catch (SQLException e) {
            throw new ServiceException("Failed to create collection of the id '" + id + "'.", e);
        }
    }

    private Collection getCollection() throws SQLException {
        return Collection.of("building")
                .setTitle("Building")
                .setDescription("A collection of the building feature type")
                .setExtent(bboxCalculator.getExtent(FeatureType.BUILDING))
                .setLinks(List.of(Link.of("http://localhost:8080/ogcapi/collections/building/items", "items")));
    }
}
