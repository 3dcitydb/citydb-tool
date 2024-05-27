package org.citydb.web.service;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.citydb.cli.util.CommandHelper;
import org.citydb.cli.util.QueryExecutor;
import org.citydb.cli.util.QueryResult;
import org.citydb.database.DatabaseManager;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.schema.FeatureType;
import org.citydb.logging.LoggerManager;
import org.citydb.model.feature.Feature;
import org.citydb.model.feature.FeatureCollection;
import org.citydb.model.geometry.Geometry;
import org.citydb.model.geometry.MultiSurface;
import org.citydb.model.geometry.Polygon;
import org.citydb.model.property.Attribute;
import org.citydb.model.property.DataType;
import org.citydb.model.walker.ModelWalker;
import org.citydb.operation.exporter.ExportException;
import org.citydb.operation.exporter.ExportOptions;
import org.citydb.operation.exporter.Exporter;
import org.citydb.operation.util.FeatureStatistics;
import org.citydb.web.config.WebOptions;
import org.citydb.web.exception.ServiceException;
import org.citydb.web.schema.geojson.FeatureCollectionGeoJSON;
import org.citydb.web.schema.geojson.FeatureGeoJSON;
import org.citydb.web.util.CrsTransformer;
import org.citydb.web.util.DatabaseController;
import org.citydb.web.util.GeoJsonConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class FeatureService {
    private final Logger logger = LoggerManager.getInstance().getLogger(FeatureService.class);
    private final DatabaseManager databaseManager = DatabaseController.getInstance().getDatabaseManager();
    private final CommandHelper helper = CommandHelper.newInstance();
    private final GeoJsonConverter geoJsonConverter = new GeoJsonConverter();
    private final WebOptions webOptions;

    @Autowired
    public FeatureService(WebOptions webOptions) {
        this.webOptions = webOptions;
    }

    @Cacheable("featureCollectionGeoJSON")
    public FeatureCollectionGeoJSON getFeatureCollectionGeoJSON(String id, Integer srid) throws ServiceException {
        org.citydb.web.config.feature.FeatureType configFeatureType = webOptions.getFeatureTypes().get(id);
        if (configFeatureType == null) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "The connection '" + id + "' is not found.");
        }

        DatabaseAdapter adapter = databaseManager.getAdapter();

        FeatureType featureType = adapter.getSchemaAdapter().getSchemaMapping()
                .getFeatureType(configFeatureType.getName(), configFeatureType.getNamespace());
        if (featureType == null) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "Feature type  '" + id + "' is not supported.");
        }

        FeatureCollectionGeoJSON featureCollectionGeoJSON = FeatureCollectionGeoJSON.newInstance();

        try (Connection connection = adapter.getPool().getConnection()) {
            FeatureCollection featureCollection = doExport(featureType);
            for (Feature feature : featureCollection.getFeatures()) {
                MultiSurface surfaces = MultiSurface.empty();
                feature.accept(new ModelWalker() {
                    @Override
                    public void visit(Polygon polygon) {
                        surfaces.getPolygons().add(polygon);
                    }
                });
                surfaces.setSRID(adapter.getDatabaseMetadata().getSpatialReference().getSRID());
                Geometry<?> transformed = srid != null ?
                        CrsTransformer.transform(surfaces, srid, connection, adapter.getGeometryAdapter()) :
                        surfaces;

                if (transformed == null) {
                    throw new ServiceException(HttpStatus.BAD_REQUEST, "The target CRS is not supported by the database");
                }

                FeatureGeoJSON featureGeoJSON = FeatureGeoJSON.of(geoJsonConverter.convert(transformed));
                Map<String, Object> properties = new HashMap<>();
                for (Attribute attribute : feature.getAttributes().getAll()) {
                    if (attribute.getDataType().isPresent()) {
                        String attrName = attribute.getName().getLocalName();
                        switch (DataType.of(attribute.getDataType().get())) {
                            case INTEGER -> properties.put(attrName, attribute.getIntValue().orElse(null));
                            case DOUBLE -> properties.put(attrName, attribute.getDoubleValue().orElse(null));
                            case STRING, CODE -> properties.put(attrName, attribute.getStringValue().orElse(null));
                            case URI -> properties.put(attrName, attribute.getURI().orElse(null));
                            case TIMESTAMP -> properties.put(attrName, attribute.getTimeStamp().orElse(null));
                            case BOOLEAN -> properties.put(attrName, 1 == attribute.getIntValue().orElse(0L));
                        }
                    }
                }

                featureGeoJSON.setProperties(properties);
                featureCollectionGeoJSON.addFeature(featureGeoJSON);
            }
        } catch (SQLException | ExportException e) {
            throw new ServiceException("Failed to export feature collection from database", e);
        } catch (Throwable e) {
            throw new ServiceException("A fatal error has occurred during while creating the feature collection", e);
        }

        return featureCollectionGeoJSON;
    }

    private FeatureCollection doExport(FeatureType featureType) throws SQLException, ExportException {
        DatabaseAdapter adapter = databaseManager.getAdapter();
        String query = "select id from " + adapter.getConnectionDetails().getSchema() + ".feature " +
                "where objectclass_id = " + featureType.getId() + " and termination_date is null";
        QueryExecutor executor = QueryExecutor.of(adapter);
        FeatureStatistics statistics = new FeatureStatistics(adapter);
        FeatureCollection featureCollection = FeatureCollection.empty();
        Exporter exporter = Exporter.newInstance();
        AtomicBoolean shouldRun = new AtomicBoolean(true);
        try (QueryResult result = executor.executeQuery(query)) {
            exporter.startSession(adapter, new ExportOptions());
            while (shouldRun.get() && result.hasNext()) {
                long id = result.getId();
                exporter.exportFeature(id).whenComplete((feature, t) -> {
                    if (feature != null) {
                        statistics.add(feature);
                        featureCollection.addFeature(feature);
                    } else {
                        if (shouldRun.get()) {
                            shouldRun.set(false);
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
        }

        return featureCollection;
    }
}
