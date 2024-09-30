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

package org.citydb.query.executor;

import org.apache.logging.log4j.Logger;
import org.citydb.core.cache.PersistentMapStore;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.util.SqlHelper;
import org.citydb.logging.LoggerManager;
import org.citydb.query.Query;
import org.citydb.query.builder.QueryBuildException;
import org.citydb.query.builder.sql.SqlBuildOptions;
import org.citydb.query.builder.sql.SqlQueryBuilder;
import org.citydb.sqlbuilder.query.Select;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

public class QueryExecutor {
    private final Select select;
    private final Select count;
    private final PersistentMapStore store;
    private final DatabaseAdapter adapter;
    private final SqlHelper helper;

    private QueryExecutor(Select select, Select count, PersistentMapStore store, DatabaseAdapter adapter) {
        this.select = select;
        this.count = count;
        this.store = store;
        this.adapter = adapter;
        helper = adapter.getSchemaAdapter().getSqlHelper();
    }

    public static Builder builder(DatabaseAdapter adapter) {
        return new Builder(adapter);
    }

    public Select getSelect() {
        return select;
    }

    public long countHits() throws SQLException {
        return helper.count(count);
    }

    public long countHits(Connection connection) throws SQLException {
        return helper.count(count, connection);
    }

    public QueryResult executeQuery() throws SQLException {
        return executeQuery(adapter.getPool().getConnection());
    }

    public QueryResult executeQuery(Connection connection) throws SQLException {
        return new QueryResult(connection, helper.prepareStatement(select, connection), store);
    }

    public static class Builder {
        private final Logger logger = LoggerManager.getInstance().getLogger(Builder.class);
        private final DatabaseAdapter adapter;
        private final SqlQueryBuilder builder;
        private Path tempDirectory;

        private Builder(DatabaseAdapter adapter) {
            this.adapter = Objects.requireNonNull(adapter, "The database adapter must not be null.");
            builder = SqlQueryBuilder.of(adapter);
        }

        public Builder tempDirectory(Path tempDirectory) {
            this.tempDirectory = tempDirectory;
            return this;
        }

        public QueryExecutor build(Query query) throws QueryBuildException, IOException {
            return build(query, SqlBuildOptions.defaults());
        }

        public QueryExecutor build(Query query, SqlBuildOptions options) throws QueryBuildException, IOException {
            Select select = builder.build(query, options);
            Select count = options.isOmitDistinct() ?
                    builder.build(query, SqlBuildOptions.of(options).omitDistinct(false)) :
                    select;

            PersistentMapStore store = null;
            if (options.isOmitDistinct() && !select.toSql().equals(count.toSql())) {
                store = PersistentMapStore.builder()
                        .tempDirectory(tempDirectory)
                        .build();
                logger.debug("Initialized distinct id cache at {}.", store.getBackingFile());
            }

            return new QueryExecutor(select, count, store, adapter);
        }
    }
}
