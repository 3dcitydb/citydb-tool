package org.citydb.web.service;

import org.citydb.web.schema.LandingPage;
import org.citydb.web.schema.Link;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PageService {
    @Value("${citydb.openapi.address}")
    private String address;

    public LandingPage getLandingPage() {
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
