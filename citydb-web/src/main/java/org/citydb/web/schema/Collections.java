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

    public List<Collection> getCollections() {
        return collections;
    }

}
