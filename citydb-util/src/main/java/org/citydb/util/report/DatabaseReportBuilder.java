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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class DatabaseReportBuilder {

    DatabaseReportBuilder() {
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
        private Exception exception;

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
                        connection), report::setTerminatedFeatures);

                countLatch.await();
                scope = options.isOnlyActiveFeatures() && report.hasTerminatedFeatures() ?
                        StatisticsHelper.FeatureScope.ACTIVE :
                        StatisticsHelper.FeatureScope.ALL;

                execute(connection -> helper.getFeatureCountAndExtent(StatisticsHelper.FeatureScope.ACTIVE,
                        connection), report::setActiveFeatures);
                execute(connection -> helper.getGeometryCount(scope, connection), report::setGeometries);
                execute(helper::getImplicitGeometryCount, result -> report.setImplicitGeometryCount(result.second()));
                execute(connection -> helper.getGeometryCountByLod(scope, connection), report::setLods);
                execute(connection -> helper.getAppearanceCountByTheme(scope, connection), report::setAppearances);
                execute(connection -> helper.hasSurfaceData(scope, connection), report::setSurfaceData);
                execute(connection -> helper.hasGlobalAppearances(scope, connection), report::setGlobalAppearances);
                execute(connection -> helper.getAddressCount(scope, connection),
                        result -> report.setAddressCount(result.second()));
                execute(this::getADEs, report::setADEs);
                execute(this::getCodeLists, report::setCodeLists);

                if (options.isIncludeGenericAttributes()) {
                    execute(connection -> helper.getGenericAttributes(scope, connection),
                            report::setGenericAttributes);
                }

                if (options.isIncludeDatabaseSize()) {
                    execute(helper::getDatabaseSize, report::setDatabaseSize);
                }

                countLatch.await();
            } finally {
                service.shutdown();
            }

            if (exception != null) {
                throw new DatabaseReportException("A database error has occurred during report generation.",
                        exception);
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

        private <T> void execute(Supplier<T> supplier, Consumer<T> consumer) {
            if (shouldRun) {
                countLatch.increment();
                service.submit(() -> {
                    try (Connection connection = adapter.getPool().getConnection(true)) {
                        consumer.accept(supplier.get(connection));
                    } catch (Exception e) {
                        shouldRun = false;
                        exception = e;
                    } finally {
                        countLatch.decrement();
                    }
                });
            }
        }
    }
}
