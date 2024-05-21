package org.citydb.web.service;

import org.citydb.web.schema.LandingPage;
import org.citydb.web.schema.Link;

import java.util.ArrayList;
import java.util.List;

public class PageService {

    public LandingPage getLandingPage() {
        Link apiDefLink = Link.of("http://localhost:8080/ogcapi", "service-desc")
                .setType("application/vnd.oai.openapi+json;version=3.0")
                .setTitle("the API definition");

        Link dataLink = Link.of("http://localhost:8080/ogcapi/collections", "data")
                .setType("application/json")
                .setTitle("Information about the feature collections");

        return LandingPage.of(new ArrayList<>(List.of(apiDefLink, dataLink)))
                .setTitle("3DCityDB OGC API")
                .setDescription("OGC Feature API for 3D City Database");
    }
}
