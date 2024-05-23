package org.citydb.web.util;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.citydb.database.DatabaseException;
import org.citydb.database.DatabaseManager;
import org.citydb.database.adapter.DatabaseAdapterException;
import org.citydb.database.adapter.DatabaseAdapterManager;
import org.citydb.database.connection.ConnectionDetails;
import org.citydb.database.postgres.PostgresqlAdapter;
import org.citydb.logging.LoggerManager;
import org.citydb.web.service.FeatureService;

import java.sql.SQLException;

public class DatabaseConnector {
    private static final DatabaseConnector instance = new DatabaseConnector();
    private final DatabaseManager databaseManager = DatabaseManager.newInstance();
    private final Logger logger = LoggerManager.getInstance().getLogger(FeatureService.class);

    private DatabaseConnector() {
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public static DatabaseConnector getInstance() {
        return instance;
    }

    public void connect(ConnectionDetails connectionDetails) throws SQLException, DatabaseException, DatabaseAdapterException {
        if (databaseManager.isConnected()) {
            return;
        }

        DatabaseAdapterManager adapterManager = DatabaseAdapterManager.newInstance();
        adapterManager.register(new PostgresqlAdapter());

        logger.info("Connecting to database " + connectionDetails.toConnectString() + ".");

        databaseManager.connect(connectionDetails, adapterManager);
        databaseManager.logDatabaseMetadata(Level.INFO);
    }
}
