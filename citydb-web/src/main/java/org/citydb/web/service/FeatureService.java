package org.citydb.web.service;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.citydb.cli.ExecutionException;
import org.citydb.cli.util.CommandHelper;
import org.citydb.core.file.OutputFile;
import org.citydb.database.DatabaseManager;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.io.IOAdapter;
import org.citydb.io.IOAdapterException;
import org.citydb.io.IOAdapterManager;
import org.citydb.io.OutputFileBuilder;
import org.citydb.io.citygml.CityGMLAdapter;
import org.citydb.io.citygml.CityJSONAdapter;
import org.citydb.io.writer.FeatureWriter;
import org.citydb.io.writer.WriteException;
import org.citydb.io.writer.WriteOptions;
import org.citydb.logging.LoggerManager;
import org.citydb.operation.exporter.ExportException;
import org.citydb.operation.exporter.ExportOptions;
import org.citydb.operation.exporter.Exporter;
import org.citydb.operation.util.FeatureStatistics;
import org.citydb.query.Query;
import org.citydb.query.executor.QueryExecutor;
import org.citydb.query.executor.QueryResult;
import org.citydb.web.config.Constants;
import org.citydb.web.exception.ServiceException;
import org.citydb.web.schema.geojson.FeatureCollectionGeoJSON;
import org.citydb.web.util.DatabaseController;
import org.citydb.web.util.GeoJSONWriter;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class FeatureService {
    private final Logger logger = LoggerManager.getInstance().getLogger(FeatureService.class);
    private final DatabaseManager databaseManager = DatabaseController.getInstance().getDatabaseManager();
    private final CommandHelper helper = CommandHelper.newInstance();


    public Path getFeatureCollectionCityGML(Query query, ExportOptions exportOptions, String contentType) throws ServiceException {
        try {
            IOAdapterManager ioManager = helper.createIOAdapterManager();
            if (ioManager.getAdapter(CityGMLAdapter.class) == null) {
                ioManager.register(new CityGMLAdapter());
            }
            if (ioManager.getAdapter(CityGMLAdapter.class) == null) {
                ioManager.register(new CityJSONAdapter());
            }
            IOAdapter ioAdapter = Objects.equals(contentType, Constants.CITYGML_MEDIA_TYPE) ?
                    ioManager.getAdapter(CityGMLAdapter.class) :
                    ioManager.getAdapter(CityJSONAdapter.class);

            String fileExtension = Objects.equals(contentType, Constants.CITYGML_MEDIA_TYPE) ? ".gml" : ".json";
            OutputFileBuilder builder = OutputFileBuilder.newInstance()
                    .defaultFileExtension(ioManager.getFileExtensions(ioAdapter).stream()
                            .findFirst()
                            .orElse(null));
            Path dataPath = Files.createTempDirectory("citydb-").resolve("data" + fileExtension);
            try (FeatureWriter writer = ioAdapter.createWriter();
                 OutputFile outputFile = builder.newOutputFile(dataPath)) {
                logger.info("Exporting to " + ioManager.getFileFormat(ioAdapter) + " file " + outputFile.getFile() + ".");
                writer.initialize(outputFile, new WriteOptions());
                doExport(query, exportOptions, writer);
            }

            return dataPath;
        } catch (IOException | WriteException | ExecutionException | IOAdapterException e) {
            throw new ServiceException("Failed to write CityGML data to file.", e);
        }
    }

    public FeatureCollectionGeoJSON getFeatureCollectionGeoJSON(Query query, ExportOptions exportOptions) throws ServiceException {
        GeoJSONWriter geoJSONWriter = new GeoJSONWriter();
        doExport(query, exportOptions, geoJSONWriter);
        return geoJSONWriter.getFeatureCollectionGeoJSON();
    }

    private void doExport(Query query, ExportOptions exportOptions, FeatureWriter writer) throws ServiceException {
        DatabaseAdapter adapter = databaseManager.getAdapter();
        Exporter exporter = Exporter.newInstance();
        AtomicBoolean shouldRun = new AtomicBoolean(true);
        FeatureStatistics statistics = new FeatureStatistics(adapter);
        try {
            QueryExecutor executor = helper.getQueryExecutor(query, databaseManager.getAdapter());
            try (QueryResult result = executor.executeQuery()) {
                exporter.startSession(adapter, exportOptions);
                while (shouldRun.get() && result.hasNext()) {
                    long id = result.getId();
                    exporter.exportFeature(id).whenComplete((feature, t) -> {
                        if (feature != null) {
                            try {
                                writer.write(feature).whenComplete((success, e) -> {
                                    if (success == Boolean.TRUE) {
                                        statistics.add(feature);
                                    } else {
                                        abort(shouldRun, id, t);
                                    }
                                });
                            } catch (Throwable e) {
                                abort(shouldRun, id, t);
                            }
                        } else {
                            abort(shouldRun, id, t);
                        }
                    });
                }
            } finally {
                exporter.closeSession();
                if (!statistics.isEmpty()) {
                    logger.info("Export summary:");
                    statistics.logFeatureSummary(Level.INFO);
                } else {
                    logger.info("No features exported.");
                }
            }
        } catch (SQLException | ExportException e) {
            throw new ServiceException("Failed to export feature collection from database", e);
        } catch (Throwable e) {
            throw new ServiceException("A fatal error has occurred during while creating the feature collection", e);
        }
    }

    private void abort(AtomicBoolean shouldRun, long id, Throwable e) {
        if (shouldRun.get()) {
            shouldRun.set(false);
            logger.warn("Database export aborted due to an error.");
            helper.logException("Failed to export feature (ID: " + id + ").", e);
        }
    }
}
