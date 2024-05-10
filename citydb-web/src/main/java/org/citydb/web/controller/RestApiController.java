package org.citydb.web.controller;

import org.citydb.model.feature.Feature;
import org.citydb.model.feature.FeatureCollection;
import org.citydb.model.feature.FeatureType;
import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.Envelope;
import org.citydb.web.management.VersionInfo;
import org.citydb.web.operation.RequestHandler;
import org.citydb.web.schema.*;
import org.citydb.web.util.BboxCalculator;
import org.citydb.web.util.CrsTransformer;
import org.citydb.web.util.DatabaseConnector;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/ogcapi")
public class RestApiController {
    private final RequestHandler requestHandler = new RequestHandler();
    private final BboxCalculator bboxCalculator = new BboxCalculator();

    @GetMapping("/")
    public ResponseEntity<VersionInfo> getVersion() {
        return new ResponseEntity<>(VersionInfo.getInstance(), HttpStatus.OK);
    }

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
    public ResponseEntity<FeatureCollectionGeoJSON> getCollectionFeatures(@PathVariable("collectionId") String collectionId) {
        FeatureCollectionGeoJSON featureCollectionGeoJSON = FeatureCollectionGeoJSON.newInstance();
        CrsTransformer crsTransformer = new CrsTransformer();
        try (Connection connection = DatabaseConnector.getInstance().getDatabaseManager().getAdapter().getPool().getConnection()) {
            FeatureCollection featureCollection = requestHandler.getFeatureCollection(FeatureType.BUILDING);
            for (Feature feature : featureCollection.getFeatures()) {
                if (feature.getEnvelope().isPresent()) {
                    Envelope envelope = feature.getEnvelope().get();
                    Envelope transformed = crsTransformer.transform(envelope, connection);
                    featureCollectionGeoJSON.addFeature(FeatureGeoJSON.of(PointGeoJSON.of(transformed.getCenter())));
                }
            }
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ResponseEntity<>(featureCollectionGeoJSON, HttpStatus.OK);
    }
}