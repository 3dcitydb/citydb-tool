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

import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.util.SqlHelper;
import org.citydb.query.Query;
import org.citydb.query.builder.QueryBuildException;
import org.citydb.query.builder.sql.SqlQueryBuilder;
import org.citydb.sqlbuilder.query.Select;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;

public class QueryExecutor {
    private final Select select;
    private final DatabaseAdapter adapter;
    private final SqlHelper helper;

    private QueryExecutor(Select select, DatabaseAdapter adapter) {
        this.select = select;
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
        return helper.count(select, adapter.getPool().getConnection());
    }

    public long countHits(Connection connection) throws SQLException {
        return helper.count(select, connection);
    }

    public QueryResult executeQuery() throws SQLException {
        return executeQuery(adapter.getPool().getConnection());
    }

    public QueryResult executeQuery(Connection connection) throws SQLException {
        PreparedStatement stmt = helper.prepareStatement(select, connection);
        return new QueryResult(connection, stmt, stmt.executeQuery());
    }

    public static class Builder {
        private final DatabaseAdapter adapter;
        private final SqlQueryBuilder builder;

        private Builder(DatabaseAdapter adapter) {
            this.adapter = Objects.requireNonNull(adapter, "The database adapter must not be null.");
            builder = SqlQueryBuilder.of(adapter);
        }

        public QueryExecutor build(Query query) throws QueryBuildException {
            return new QueryExecutor(builder.build(query), adapter);
        }
    }
}
