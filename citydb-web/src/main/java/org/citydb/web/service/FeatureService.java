package org.citydb.web.service;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.citydb.cli.util.CommandHelper;
import org.citydb.cli.util.QueryExecutor;
import org.citydb.cli.util.QueryResult;
import org.citydb.database.DatabaseManager;
import org.citydb.logging.LoggerManager;
import org.citydb.model.feature.Feature;
import org.citydb.model.feature.FeatureCollection;
import org.citydb.model.feature.FeatureType;
import org.citydb.model.geometry.Geometry;
import org.citydb.model.geometry.MultiSurface;
import org.citydb.model.geometry.Polygon;
import org.citydb.model.walker.ModelWalker;
import org.citydb.operation.exporter.ExportException;
import org.citydb.operation.exporter.ExportOptions;
import org.citydb.operation.exporter.Exporter;
import org.citydb.operation.util.FeatureStatistics;
import org.citydb.web.schema.geojson.FeatureCollectionGeoJSON;
import org.citydb.web.schema.geojson.FeatureGeoJSON;
import org.citydb.web.schema.geojson.GeometryGeoJSON;
import org.citydb.web.util.CrsTransformer;
import org.citydb.web.util.DatabaseConnector;
import org.citydb.web.util.GeoJsonConverter;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class FeatureService {
    private final Logger logger = LoggerManager.getInstance().getLogger(FeatureService.class);
    private final DatabaseManager databaseManager = DatabaseConnector.getInstance().getDatabaseManager();
    private final CommandHelper helper = CommandHelper.newInstance();
    private volatile boolean shouldRun = true;
    private final GeoJsonConverter geoJsonConverter = new GeoJsonConverter();

    @Cacheable("featureCollectionGeoJSON")
    public FeatureCollectionGeoJSON getFeatureCollectionGeoJSON(String collectionId, Integer srid) throws ServiceException {
        FeatureType featureType = Arrays.stream(FeatureType.values())
                .filter(type -> type.getName().getLocalName().equalsIgnoreCase(collectionId))
                .findFirst()
                .orElse(null);

        if (featureType == null) {
            throw new ServiceException("No features are found for the collection '" + collectionId + "'.");
        }

        FeatureCollectionGeoJSON featureCollectionGeoJSON = FeatureCollectionGeoJSON.newInstance();
        CrsTransformer crsTransformer = new CrsTransformer();
        try (Connection connection = databaseManager.getAdapter().getPool().getConnection()) {
            FeatureCollection featureCollection = getFeatureCollection(featureType);
            for (Feature feature : featureCollection.getFeatures()) {
                if (feature.getEnvelope().isPresent()) {
                    MultiSurface surfaces = MultiSurface.empty();
                    feature.accept(new ModelWalker() {
                        @Override
                        public void visit(Polygon polygon) {
                            surfaces.getPolygons().add(polygon);
                        }
                    });
                    surfaces.setSRID(databaseManager.getAdapter().getDatabaseMetadata().getSpatialReference().getSRID());
                    Geometry<?> transformed = srid != null ? crsTransformer.transform(surfaces, srid, connection) : surfaces;
                    GeometryGeoJSON geometryGeoJSON = geoJsonConverter.convert(transformed);
                    FeatureGeoJSON featureGeoJSON = FeatureGeoJSON.of(geometryGeoJSON);
                    featureGeoJSON.getProperties().put("id", feature.getObjectId().orElse(null));
                    featureCollectionGeoJSON.addFeature(featureGeoJSON);
                }
            }
        } catch (Throwable e) {
            throw new ServiceException("A fatal error has occurred during export. GeoJSON feature collection", e);
        }
        return featureCollectionGeoJSON;
    }

    private FeatureCollection getFeatureCollection(FeatureType featureType) throws SQLException, ExportException {
        FeatureCollection featureCollection = FeatureCollection.empty();
        String schema = databaseManager.getAdapter().getConnectionDetails().getSchema();
        int featureTypeId = databaseManager.getAdapter().getSchemaAdapter().getSchemaMapping().getFeatureType(featureType.getName()).getId();
        String query = "select id from " + schema + ".feature " +
                "where objectclass_id = " + featureTypeId + " and termination_date is null";
        QueryExecutor executor = QueryExecutor.of(databaseManager.getAdapter());
        FeatureStatistics statistics = new FeatureStatistics(databaseManager.getAdapter());
        Exporter exporter = Exporter.newInstance();
        AtomicLong counter = new AtomicLong();
        try (QueryResult result = executor.executeQuery(query)) {
            exporter.startSession(databaseManager.getAdapter(), new ExportOptions());
            while (shouldRun && result.hasNext()) {
                long id = result.getId();
                exporter.exportFeature(id).whenComplete((feature, t) -> {
                    if (feature != null) {
                        statistics.add(feature);
                        featureCollection.addFeature(feature);
                        long count = counter.incrementAndGet();
                        if (count % 1000 == 0) {
                            logger.info(count + " features exported.");
                        }
                    } else {
                        if (shouldRun) {
                            shouldRun = false;
                            logger.warn("Database export aborted due to an error.");
                            helper.logException("Failed to export feature (ID: " + id + ")." , t);
                        }
                    }
                });
            }
        }  finally {
            exporter.closeSession();
            if (!statistics.isEmpty()) {
                logger.info("Export summary:");
                statistics.logFeatureSummary(Level.INFO);
            } else {
                logger.info("No features exported.");
            }
            logger.info("Bounding box calculation successfully completed.");
        }
        return featureCollection;
    }
}
