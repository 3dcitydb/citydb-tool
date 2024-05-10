package org.citydb.web.controller;

import org.citydb.model.feature.FeatureCollection;
import org.citydb.model.feature.FeatureType;
import org.citydb.web.operation.RequestHandler;
import org.citydb.web.schema.Collection;
import org.citydb.web.schema.Extent;
import org.citydb.web.util.BboxCalculator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/ogcapi")
public class RestApiController {
    private final RequestHandler requestHandler = new RequestHandler();
    private final BboxCalculator bboxCalculator = new BboxCalculator();

    @GetMapping("/collections")
    public ResponseEntity<List<Collection>> getCollections() {
        try {
            Extent extent = bboxCalculator.getExtent(FeatureType.BUILDING);
            List<Collection> collections = new ArrayList<>(
                    List.of(Collection.of("1")
                            .setTitle("collection 1")
                            .setDescription("my first test collection")
                            .setExtent(extent)
                    )
            );
            return new ResponseEntity<>(collections, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/collections/{collectionId}/items")
    public ResponseEntity<List<FeatureCollection>> getCollectionFeatures(@PathVariable("collectionId") String collectionId) {
        FeatureCollection featureCollection;
        try {
            featureCollection = requestHandler.getFeatureCollection(FeatureType.BUILDING);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ResponseEntity<>(List.of(featureCollection), HttpStatus.OK);
    }
}