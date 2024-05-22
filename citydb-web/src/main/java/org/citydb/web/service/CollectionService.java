package org.citydb.web.service;

import org.citydb.web.config.WebOptions;
import org.citydb.web.schema.Collection;
import org.citydb.web.schema.Collections;
import org.citydb.web.schema.Link;

import java.util.List;

import static java.util.Collections.singletonList;

public class CollectionService {
    private final WebOptions webOptions = WebOptions.getInstance();
    private Collections collections;

    public Collections getCollections() {
        if (collections == null) {
            initialize();
        }

        return collections;
    }

    private void initialize() {
        String address = webOptions.getCurrentAddress();

        List<Link> linkList = singletonList(Link.of(address + "/collections", "items")
                        .setType("application/geo+json"));

        List<Collection> collectionList = webOptions.getFeatureTypes().getFeatureTypes().stream()
                .map(featureType -> Collection.of(featureType.getId())
                        .setLinks(List.of(Link.of(address + "/collections/" + featureType.getId() + "/items", "items"))))
                .toList();

        collections = Collections.of(linkList, collectionList);
    }

    public Collection getCollection(String id) throws ServiceException {
        Collection collection = getCollections().findCollectionById(id);
        if (collection == null) {
            throw new ServiceException("Feature collection '" + id + "' not found.");
        }

        return collection;
    }
}
