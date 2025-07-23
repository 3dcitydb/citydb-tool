/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
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

package org.citydb.util.report;

import org.citydb.core.concurrent.CountLatch;
import org.citydb.core.tuple.Pair;
import org.citydb.database.DatabaseException;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.util.StatisticsHelper;
import org.citydb.model.geometry.Envelope;
import org.citydb.sqlbuilder.query.Select;
import org.citydb.sqlbuilder.schema.Table;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DatabaseReportBuilder {

    DatabaseReportBuilder() {
    }

    static DatabaseReportBuilder newInstance() {
        return new DatabaseReportBuilder();
    }

    DatabaseReport build(ReportOptions options, DatabaseAdapter adapter) throws DatabaseReportException {
        return new ReportProcessor(options, adapter).execute();
    }

    @FunctionalInterface
    private interface Supplier<T> {
        T get(Connection connection) throws DatabaseException, SQLException;
    }

    private static class ReportProcessor {
        private final ReportOptions options;
        private final DatabaseAdapter adapter;
        private final StatisticsHelper helper;
        private final String schema;

        private volatile boolean shouldRun = true;
        private ExecutorService service;
        private CountLatch countLatch;
        private DatabaseReportException exception;

        ReportProcessor(ReportOptions options, DatabaseAdapter adapter) {
            this.options = Objects.requireNonNull(options, "The report options must not be null.");
            this.adapter = Objects.requireNonNull(adapter, "The database adapter must not be null.");
            helper = adapter.getSchemaAdapter().getStatisticsHelper();
            schema = adapter.getConnectionDetails().getSchema();
        }

        DatabaseReport execute() throws DatabaseReportException {
            service = Executors.newFixedThreadPool(options.getNumberOfThreads() > 0 ?
                    options.getNumberOfThreads() : 4);
            countLatch = new CountLatch();

            DatabaseReport report = new DatabaseReport(options, adapter);
            StatisticsHelper.FeatureScope scope;

            try {
                execute(connection -> helper.getFeatureCountAndExtent(StatisticsHelper.FeatureScope.TERMINATED,
                        connection))
                        .thenAccept(report::addTerminatedFeatures);

                countLatch.await();
                scope = options.isOnlyActiveFeatures() && report.hasTerminatedFeatures() ?
                        StatisticsHelper.FeatureScope.ACTIVE :
                        StatisticsHelper.FeatureScope.ALL;

                execute(connection -> helper.getFeatureCountAndExtent(StatisticsHelper.FeatureScope.ACTIVE, connection))
                        .thenAccept(report::addActiveFeatures);
                execute(connection -> helper.getGeometryCount(scope, connection))
                        .thenAccept(report::addGeometries);
                execute(helper::getImplicitGeometryCount)
                        .thenAccept(result -> report.setImplicitGeometryCount(result.second()));
                execute(connection -> helper.getGeometryCountByLod(scope, connection))
                        .thenAccept(report::addLods);
                execute(connection -> helper.getAppearanceCountByTheme(scope, connection))
                        .thenAccept(report::addAppearances);
                execute(connection -> helper.hasSurfaceData(scope, connection))
                        .thenAccept(report::setSurfaceData);
                execute(connection -> helper.hasGlobalAppearances(scope, connection))
                        .thenAccept(report::setGlobalAppearances);
                execute(connection -> helper.getAddressCount(scope, connection))
                        .thenAccept(result -> report.setAddressCount(result.second()));
                execute(this::getADEs)
                        .thenAccept(report::addADEs);
                execute(this::getCodeLists)
                        .thenAccept(report::addCodeLists);

                if (options.isIncludeGenericAttributes()) {
                    execute(connection -> helper.getGenericAttributes(scope, connection))
                            .thenAccept(report::addGenericAttributes);
                }

                if (options.isIncludeDatabaseSize()) {
                    execute(helper::getDatabaseSize)
                            .thenAccept(report::setDatabaseSize);
                }

                countLatch.await();
            } finally {
                service.shutdown();
            }

            if (exception != null) {
                throw exception;
            }

            report.setWgs84Extent(transformExtent(report.getExtent()));
            return report;
        }

        private Map<String, Pair<String, String>> getADEs(Connection connection) throws SQLException {
            Table ade = Table.of(org.citydb.database.schema.Table.ADE.getName(), schema);

            Select select = Select.newInstance()
                    .select(ade.columns("name", "description", "version"))
                    .from(ade);

            Map<String, Pair<String, String>> ades = new HashMap<>();
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(select.toSql())) {
                while (rs.next()) {
                    ades.put(rs.getString(1), Pair.of(rs.getString(2), rs.getString(3)));
                }
            }

            return ades;
        }

        private Map<String, Set<String>> getCodeLists(Connection connection) throws SQLException {
            Table codeList = Table.of(org.citydb.database.schema.Table.CODELIST.getName(), schema);

            Select select = Select.newInstance()
                    .select(codeList.columns("codelist_type", "url"))
                    .from(codeList);

            Map<String, Set<String>> codeLists = new HashMap<>();
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(select.toSql())) {
                while (rs.next()) {
                    codeLists.computeIfAbsent(rs.getString(1), k -> new HashSet<>()).add(rs.getString(2));
                }
            }

            return codeLists;
        }

        private Envelope transformExtent(Envelope extent) throws DatabaseReportException {
            if (extent != null && !extent.isEmpty()) {
                try {
                    return adapter.getGeometryAdapter().transform(extent, 4326);
                } catch (Exception e) {
                    throw new DatabaseReportException("Failed to transform extent to WGS84.", e);
                }
            }

            return null;
        }

        private <T> CompletableFuture<T> execute(Supplier<T> supplier) {
            CompletableFuture<T> result = new CompletableFuture<>();
            if (shouldRun) {
                countLatch.increment();
                service.submit(() -> {
                    try (Connection connection = adapter.getPool().getConnection(true)) {
                        result.complete(supplier.get(connection));
                    } catch (Exception e) {
                        shouldRun = false;
                        result.cancel(true);
                        exception = new DatabaseReportException("A database error has occurred during report " +
                                "generation.", e);
                    } finally {
                        countLatch.decrement();
                    }
                });
            } else {
                result.cancel(true);
            }

            return result;
        }
    }
}
