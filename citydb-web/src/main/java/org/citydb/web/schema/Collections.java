package org.citydb.web.schema;

import java.util.List;

public class Collections {
    private List<Link> links;
    private List<Collection> collections;

    private Collections(List<Link> links, List<Collection> collections) {
        this.links = links;
        this.collections = collections;
    }

    public static Collections of(List<Link> links, List<Collection> collections) {
        return new Collections(links, collections);
    }

    public List<Link> getLinks() {
        return links;
    }

    public Collections setLinks(List<Link> links) {
        this.links = links;
        return this;
    }

    public List<Collection> getCollections() {
        return collections;
    }

    public Collections setCollections(List<Collection> collections) {
        this.collections = collections;
        return this;
    }

    public Collection findCollectionById(String id) {
        return collections.stream().filter(collection -> collection.getId().equals(id)).findFirst().orElse(null);
    }
}
