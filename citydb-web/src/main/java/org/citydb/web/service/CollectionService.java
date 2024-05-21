package org.citydb.web.service;

import org.citydb.web.config.WebOptions;
import org.citydb.web.schema.Collection;
import org.citydb.web.schema.Collections;

public class CollectionService {
    private final WebOptions webOptions = new WebOptions();

    public Collections getCollections() {
        return webOptions.getFeatureCollections();
    }

    public Collection getCollection(String id) throws ServiceException {

        Collection collection = getCollections().findCollectionById(id);
        if (collection == null) {
            throw new ServiceException("Feature collection '" + id + "' not found.");
        }

        return collection;
    }
}
