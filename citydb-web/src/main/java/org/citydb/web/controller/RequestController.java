package org.citydb.web.controller;

import org.citydb.model.feature.FeatureType;
import org.citydb.web.management.VersionInfo;
import org.citydb.web.service.ExportHandler;
import org.citydb.web.schema.Collection;
import org.citydb.web.schema.Collections;
import org.citydb.web.schema.LandingPage;
import org.citydb.web.schema.Link;
import org.citydb.web.schema.geojson.FeatureCollectionGeoJSON;
import org.citydb.web.util.BboxCalculator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/ogcapi")
public class RequestController {
    private final ExportHandler exportHandler = new ExportHandler();
    private final BboxCalculator bboxCalculator = new BboxCalculator();

    @GetMapping("")
    public ResponseEntity<LandingPage> getLandingPage() {
        Link apiDefLink = Link.of("http://localhost:8080/ogcapi", "service-desc")
                .setType("application/vnd.oai.openapi+json;version=3.0")
                .setTitle("the API definition");

        Link dataLink = Link.of("http://localhost:8080/ogcapi/collections", "data")
                .setType("application/json")
                .setTitle("Information about the feature collections");

        LandingPage landingPage = LandingPage.of(new ArrayList<>(List.of(apiDefLink, dataLink)))
                .setTitle("3DCityDB OGC API")
                .setDescription("OGC Feature API for 3D City Database");

        return new ResponseEntity<>(landingPage, HttpStatus.OK);
    }

    @GetMapping("/version")
    public ResponseEntity<VersionInfo> getVersion() {
        return new ResponseEntity<>(VersionInfo.getInstance(), HttpStatus.OK);
    }

    @GetMapping("/collections")
    public ResponseEntity<Collections> getCollections() {
        try {
            List<Link> links = java.util.Collections.singletonList(
                    Link.of("http://localhost:8080/ogcapi/collections/1/items", "items")
                    .setType("application/geo+json")
                    .setTitle("Buildings")
            );
            List<Collection> collectionList = new ArrayList<>(List.of(getCollection()));
            Collections collections = Collections.of(links, collectionList);
            return new ResponseEntity<>(collections, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/collections/{collectionId}")
    public ResponseEntity<Collection> getCollection(@PathVariable("collectionId") String collectionId) {
        try {
            return new ResponseEntity<>(getCollection(), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/collections/{collectionId}/items")
    public ResponseEntity<FeatureCollectionGeoJSON> getCollectionFeatures(@PathVariable("collectionId") String collectionId) {
        FeatureCollectionGeoJSON featureCollectionGeoJSON;
        try {
            featureCollectionGeoJSON = exportHandler.getFeatureCollectionGeoJSON(collectionId);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ResponseEntity<>(featureCollectionGeoJSON, HttpStatus.OK);
    }

    private Collection getCollection() throws SQLException {
        return Collection.of("1")
                .setTitle("collection 1")
                .setDescription("my first test collection")
                .setExtent(bboxCalculator.getExtent(FeatureType.BUILDING))
                .setLinks(List.of(Link.of("http://localhost:8080/ogcapi/collections/1/items", "items")));
    }
}