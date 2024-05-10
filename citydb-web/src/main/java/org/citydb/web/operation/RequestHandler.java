package org.citydb.web.operation;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.citydb.cli.util.CommandHelper;
import org.citydb.cli.util.QueryExecutor;
import org.citydb.cli.util.QueryResult;
import org.citydb.database.DatabaseManager;
import org.citydb.logging.LoggerManager;
import org.citydb.model.feature.FeatureCollection;
import org.citydb.model.feature.FeatureType;
import org.citydb.operation.exporter.ExportOptions;
import org.citydb.operation.exporter.Exporter;
import org.citydb.operation.util.FeatureStatistics;
import org.citydb.web.exception.OperationException;
import org.citydb.web.util.DatabaseConnector;

import java.util.concurrent.atomic.AtomicLong;

public class RequestHandler {
    private final Logger logger = LoggerManager.getInstance().getLogger(RequestHandler.class);
    private final DatabaseManager databaseManager = DatabaseConnector.getInstance().getDatabaseManager();
    private final CommandHelper helper = CommandHelper.newInstance();
    private volatile boolean shouldRun = true;

    public FeatureCollection getFeatureCollection(FeatureType featureType) throws OperationException {
        FeatureCollection featureCollection = FeatureCollection.empty();
        String schema = databaseManager.getAdapter().getConnectionDetails().getSchema();
        int featureTypeId = databaseManager.getAdapter().getSchemaAdapter().getSchemaMapping().getFeatureType(featureType.getName()).getId();
        String query = "select id from " + schema + ".feature " +
                "where objectclass_id = " + featureTypeId + " and termination_date is null";
        QueryExecutor executor = QueryExecutor.of(databaseManager.getAdapter());
        FeatureStatistics statistics = new FeatureStatistics(databaseManager.getAdapter());
        Exporter exporter = Exporter.newInstance();
        AtomicLong counter = new AtomicLong();
        try {
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
        } catch (Throwable e) {
            logger.warn("Database export aborted due to an error.");
            throw new OperationException("A fatal error has occurred during export.", e);
        }
        return featureCollection;
    }
}
