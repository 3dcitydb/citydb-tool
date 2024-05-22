package org.citydb.web.service;

import org.citydb.web.config.WebOptions;
import org.citydb.web.schema.LandingPage;
import org.citydb.web.schema.Link;

import java.util.ArrayList;
import java.util.List;

public class PageService {
    private final WebOptions webOptions = WebOptions.getInstance();

    public LandingPage getLandingPage() {
        String address = webOptions.getCurrentAddress();

        Link apiDefLink = Link.of(address, "service-desc")
                .setType("application/vnd.oai.openapi+json;version=3.0")
                .setTitle("the API definition");

        Link dataLink = Link.of(address + "/collections", "data")
                .setType("application/json")
                .setTitle("Information about the feature collections");

        return LandingPage.of(new ArrayList<>(List.of(apiDefLink, dataLink)))
                .setTitle("3DCityDB OGC API")
                .setDescription("OGC Feature API for 3D City Database");
    }
}
