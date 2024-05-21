package org.citydb.web.controller;

import org.citydb.web.schema.Collection;
import org.citydb.web.schema.Collections;
import org.citydb.web.schema.LandingPage;
import org.citydb.web.schema.geojson.FeatureCollectionGeoJSON;
import org.citydb.web.service.CollectionService;
import org.citydb.web.service.FeatureService;
import org.citydb.web.service.PageService;
import org.citydb.web.service.VersionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ogcapi")
public class ApiController {
    private final PageService pageService = new PageService();
    private final FeatureService featureService = new FeatureService();
    private final CollectionService collectionService = new CollectionService();


    @GetMapping("")
    public ResponseEntity<LandingPage> getLandingPage() {
        return new ResponseEntity<>(pageService.getLandingPage(), HttpStatus.OK);
    }

    @GetMapping("/version")
    public ResponseEntity<VersionService> getVersion() {
        return new ResponseEntity<>(VersionService.getInstance(), HttpStatus.OK);
    }

    @GetMapping("/collections")
    public ResponseEntity<Collections> getCollections() {
        try {
            return new ResponseEntity<>(collectionService.getCollections(), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/collections/{collectionId}")
    public ResponseEntity<Collection> getCollection(@PathVariable("collectionId") String collectionId) {
        try {
            return new ResponseEntity<>(collectionService.getCollection(collectionId), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/collections/{collectionId}/items")
    public ResponseEntity<FeatureCollectionGeoJSON> getCollectionFeatures(@PathVariable("collectionId") String collectionId) {
        try {
            return new ResponseEntity<>(featureService.getFeatureCollectionGeoJSON(collectionId), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}