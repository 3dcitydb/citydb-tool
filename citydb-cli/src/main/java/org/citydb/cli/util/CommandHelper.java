/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
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
import org.citydb.cli.option.DatabaseOptions;
import org.citydb.database.DatabaseException;
import org.citydb.database.DatabaseManager;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.connection.ConnectionDetails;
import org.citydb.database.schema.Index;
import org.citydb.database.schema.IndexHelper;
import org.citydb.io.IOAdapterException;
import org.citydb.io.IOAdapterManager;
import org.citydb.logging.LoggerManager;
import org.citydb.operation.util.FeatureStatistics;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class CommandHelper {
    private final Logger logger;

    private CommandHelper(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "The logger must not be null.");
    }

    public static CommandHelper of(Logger logger) {
        return new CommandHelper(logger);
    }

    public DatabaseManager connect(DatabaseOptions options) throws ExecutionException {
        return connect(options.toConnectionDetails());
    }

    public DatabaseManager connect(ConnectionDetails connectionDetails) throws ExecutionException {
        try {
            logger.info("Connecting to database " + connectionDetails.toConnectString() + ".");
            DatabaseManager databaseManager = DatabaseManager.newInstance();
            databaseManager.connect(connectionDetails);
            databaseManager.printDatabaseMetadata(logger::info);
            return databaseManager;
        } catch (DatabaseException | SQLException e) {
            throw new ExecutionException("Failed to connect to the database.", e);
        }
    }

    public IOAdapterManager createIOAdapterManager() throws ExecutionException {
        IOAdapterManager manager = IOAdapterManager.newInstance().load();
        if (manager.hasExceptions()) {
            throw new ExecutionException("Failed to initialize IO adapter manager.",
                    manager.getExceptions().values().stream()
                            .flatMap(Collection::stream)
                            .findFirst().orElse(new IOAdapterException()));
        }

        return manager;
    }

    public FeatureStatistics createFeatureStatistics(DatabaseAdapter adapter) throws ExecutionException {
        try {
            return new FeatureStatistics(adapter.getSchemaAdapter().getObjectClassHelper(),
                    adapter.getSchemaAdapter().getNamespaceHelper());
        } catch (SQLException e) {
            throw new ExecutionException("Failed to create feature statistics helper.", e);
        }
    }

    public void createIndexes(DatabaseAdapter adapter) throws ExecutionException {
        try {
            IndexHelper indexHelper = adapter.getSchemaAdapter().getIndexHelper();
            List<Index> indexes = IndexHelper.DEFAULT_INDEXES;
            for (int i = 0; i < indexes.size(); i++) {
                Index index = indexes.get(i);
                logger.debug("Creating database index " + (i + 1) + " of " + indexes.size() + " on " + index + ".");
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
                logger.debug("Dropping database index " + (i + 1) + " of " + indexes.size() + " on " + index + ".");
                indexHelper.drop(index);
            }
        } catch (SQLException e) {
            throw new ExecutionException("Failed to drop database indexes.", e);
        }
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
