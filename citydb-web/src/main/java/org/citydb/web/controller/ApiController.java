package org.citydb.web.controller;

import org.citydb.web.schema.Collection;
import org.citydb.web.schema.Collections;
import org.citydb.web.schema.LandingPage;
import org.citydb.web.schema.geojson.FeatureCollectionGeoJSON;
import org.citydb.web.service.CollectionService;
import org.citydb.web.service.FeatureService;
import org.citydb.web.service.PageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${citydb.openapi.context-path}")
public class ApiController {
    private PageService pageService;
    private FeatureService featureService;
    private CollectionService collectionService;

    @Autowired
    public void setPageService(PageService service) {
        this.pageService = service;
    }

    @Autowired
    public void setFeatureService(FeatureService service) {
        this.featureService = service;
    }

    @Autowired
    public void setCollectionService(CollectionService service) {
        this.collectionService = service;
    }

    @GetMapping("")
    public ResponseEntity<LandingPage> getLandingPage() {
        return new ResponseEntity<>(pageService.getLandingPage(), HttpStatus.OK);
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
    public ResponseEntity<FeatureCollectionGeoJSON> getCollectionFeatures(
            @PathVariable("collectionId") String collectionId, @RequestParam(value = "srid", required = false) Integer srid
    ) {
        try {
            return new ResponseEntity<>(featureService.getFeatureCollectionGeoJSON(collectionId, srid), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}