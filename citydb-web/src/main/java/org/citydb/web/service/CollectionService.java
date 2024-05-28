package org.citydb.web.service;

import jakarta.servlet.http.HttpServletRequest;
import org.citydb.web.config.WebOptions;
import org.citydb.web.exception.ServiceException;
import org.citydb.web.schema.Collection;
import org.citydb.web.schema.Collections;
import org.citydb.web.schema.Link;
import org.citydb.web.util.ServerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

import static java.util.Collections.singletonList;

@Service
public class CollectionService {
    private Collections collections;
    private WebOptions webOptions;

    @Autowired
    public void setWebOptions(WebOptions webOptions) {
        this.webOptions = webOptions;
    }

    @Cacheable("collections")
    public Collections getCollections(HttpServletRequest request) throws ServiceException {
        if (collections == null) {
            initialize(request);
        }

        return collections;
    }

    private void initialize(HttpServletRequest request) throws ServiceException {
        String address = ServerUtil.getServiceURL(request) + "/collections";

        List<Link> linkList = singletonList(Link.of(address, "items").setType("application/geo+json"));
        List<Collection> collectionList = webOptions.getFeatureTypes().entrySet().stream()
                .map(entry -> Collection.of(entry.getKey())
                        .setTitle(entry.getValue().getName())
                        .setDescription(entry.getValue().getNamespace())
                        .setLinks(List.of(Link.of(address + "/"+ entry.getKey() + "/items", "items"))))
                .toList();

        collections = Collections.of(linkList, collectionList);
    }

    public Collection getCollection(String id, HttpServletRequest request) throws ServiceException {
        Collection collection = getCollections(request).findCollectionById(id);
        if (collection == null) {
            throw new ServiceException("Feature collection '" + id + "' not found.");
        }

        return collection;
    }
}
