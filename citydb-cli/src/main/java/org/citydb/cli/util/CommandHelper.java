/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
 * virtualcitysystems GmbH, Germany
 * https://vc.systems/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.citydb.cli.util;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.citydb.cli.ExecutionException;
import org.citydb.cli.common.ConnectionOptions;
import org.citydb.config.Config;
import org.citydb.config.ConfigException;
import org.citydb.core.CoreConstants;
import org.citydb.database.DatabaseException;
import org.citydb.database.DatabaseManager;
import org.citydb.database.DatabaseOptions;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.connection.ConnectionDetails;
import org.citydb.database.schema.Index;
import org.citydb.database.util.IndexHelper;
import org.citydb.io.IOAdapterException;
import org.citydb.io.IOAdapterManager;
import org.citydb.logging.LoggerManager;
import org.citydb.plugin.PluginManager;
import org.citydb.query.Query;
import org.citydb.query.builder.QueryBuildException;
import org.citydb.query.builder.sql.SqlBuildOptions;
import org.citydb.query.executor.QueryExecutor;
import org.citydb.sqlbuilder.common.SqlObject;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

public class CommandHelper {
    private final Logger logger = LoggerManager.getInstance().getLogger(CommandHelper.class);
    private final PluginManager pluginManager = PluginManager.getInstance();

    private CommandHelper() {
    }

    public static CommandHelper newInstance() {
        return new CommandHelper();
    }

    public DatabaseManager connect(ConnectionOptions options, Config config) throws ExecutionException {
        ConnectionDetails connectionDetails = options != null ?
                options.toConnectionDetails() :
                new ConnectionDetails();

        DatabaseOptions databaseOptions;
        try {
            databaseOptions = config.get(DatabaseOptions.class);
        } catch (ConfigException e) {
            throw new ExecutionException("Failed to get database options from config.", e);
        }

        if (databaseOptions != null) {
            databaseOptions.getDefaultConnection().ifPresent(defaults -> connectionDetails
                    .setHost(connectionDetails.getHost(defaults.getHost()))
                    .setPort(connectionDetails.getPort(defaults.getPort()))
                    .setDatabase(connectionDetails.getDatabase(defaults.getDatabase()))
                    .setSchema(connectionDetails.getSchema(defaults.getSchema()))
                    .setUser(connectionDetails.getUser(defaults.getUser()))
                    .setPassword(connectionDetails.getPassword(defaults.getPassword()))
                    .setPoolOptions(defaults.getPoolOptions().orElse(null)));
        }

        return connect(connectionDetails);
    }

    public DatabaseManager connect(ConnectionOptions options) throws ExecutionException {
        return connect(options != null ? options.toConnectionDetails() : new ConnectionDetails());
    }

    public DatabaseManager connect(ConnectionDetails connectionDetails) throws ExecutionException {
        try {
            connectionDetails.fillAbsentValuesFromEnv();
            logger.info("Connecting to database {}.", connectionDetails.toConnectString());
            DatabaseManager databaseManager = DatabaseManager.newInstance();
            databaseManager.connect(connectionDetails);
            databaseManager.logDatabaseMetadata(Level.INFO);
            return databaseManager;
        } catch (DatabaseException | SQLException e) {
            throw new ExecutionException("Failed to connect to the database.", e);
        }
    }

    public IOAdapterManager createIOAdapterManager() throws ExecutionException {
        IOAdapterManager manager = IOAdapterManager.newInstance().load(pluginManager.getClassLoader());
        if (manager.hasExceptions()) {
            throw new ExecutionException("Failed to initialize IO adapter manager.",
                    manager.getExceptions().values().stream()
                            .flatMap(Collection::stream)
                            .findFirst().orElse(new IOAdapterException()));
        }

        return manager;
    }

    public QueryExecutor getQueryExecutor(Query query, SqlBuildOptions options, DatabaseAdapter adapter) throws ExecutionException {
        return getQueryExecutor(query, options, null, adapter);
    }

    public QueryExecutor getQueryExecutor(Query query, SqlBuildOptions options, Path tempDirectory, DatabaseAdapter adapter) throws ExecutionException {
        try {
            return QueryExecutor.builder(adapter)
                    .tempDirectory(tempDirectory)
                    .build(query, options);
        } catch (QueryBuildException e) {
            throw new ExecutionException("Failed to build database query.", e);
        } catch (IOException e) {
            throw new ExecutionException("Failed to build database query executor.", e);
        }
    }

    public String getFormattedSql(SqlObject object, DatabaseAdapter adapter) {
        return adapter.getSchemaAdapter().getSqlHelper().toSql(object, org.citydb.sqlbuilder.SqlBuildOptions.defaults()
                .setKeywordCase(org.citydb.sqlbuilder.SqlBuildOptions.KeywordCase.UPPERCASE)
                .setIndent("  "));
    }

    public void createIndexes(DatabaseAdapter adapter) throws ExecutionException {
        try {
            IndexHelper indexHelper = adapter.getSchemaAdapter().getIndexHelper();
            List<Index> indexes = IndexHelper.DEFAULT_INDEXES;
            for (int i = 0; i < indexes.size(); i++) {
                Index index = indexes.get(i);
                logger.debug("Creating database index {} of {} on {}.", i + 1, indexes.size(), index);
                indexHelper.create(index);
            }
        } catch (SQLException e) {
            throw new ExecutionException("Failed to create database indexes.", e);
        }
    }

    public void dropIndexes(DatabaseAdapter adapter) throws ExecutionException {
        try {
            IndexHelper indexHelper = adapter.getSchemaAdapter().getIndexHelper();
            List<Index> indexes = IndexHelper.DEFAULT_INDEXES;
            for (int i = 0; i < indexes.size(); i++) {
                Index index = indexes.get(i);
                logger.debug("Dropping database index {} of {} on {}.", i + 1, indexes.size(), index);
                indexHelper.drop(index);
            }
        } catch (SQLException e) {
            throw new ExecutionException("Failed to drop database indexes.", e);
        }
    }

    public void logIndexStatus(Level level, DatabaseAdapter adapter) throws ExecutionException {
        printIndexStatus(adapter, s -> logger.log(level, s));
    }

    public void printIndexStatus(DatabaseAdapter adapter, Consumer<String> consumer) throws ExecutionException {
        try {
            String status = switch (adapter.getSchemaAdapter().getIndexHelper().existAll(IndexHelper.DEFAULT_INDEXES)) {
                case ON -> "on";
                case PARTIALLY_ON -> "partially on";
                default -> "off";
            };

            consumer.accept("Database indexes are " + status + ".");
        } catch (SQLException e) {
            throw new ExecutionException("Failed to query status of database indexes.", e);
        }
    }

    public Path resolveDirectory(Path path) {
        return path != null ? CoreConstants.WORKING_DIR.resolve(path) : null;
    }

    public synchronized void logException(String message, Throwable e) {
        logger.error(message, e);
        Level level = LoggerManager.getInstance().logConsole().getLogLevel();
        if (level.isMoreSpecificThan(Level.INFO)) {
            while (e != null) {
                logger.error(e);
                e = e.getCause();
            }
        }
    }

    public synchronized void logException(Throwable e) {
        if (e instanceof ExecutionException) {
            String message = e.getMessage() != null ?
                    e.getMessage() :
                    "An unexpected error has occurred during execution.";
            logException(message, e.getCause());
        } else {
            logException("An unexpected " + e.getClass().getName() + " error has occurred during execution.", e);
        }
    }
}
