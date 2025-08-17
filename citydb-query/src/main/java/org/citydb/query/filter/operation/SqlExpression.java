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

package org.citydb.query.filter.operation;

import org.citydb.query.filter.common.FilterVisitor;
import org.citydb.query.filter.literal.StringLiteral;
import org.citydb.sqlbuilder.query.QueryStatement;

import java.util.Objects;

public class SqlExpression implements BooleanExpression {
    private final StringLiteral queryExpression;

    private SqlExpression(StringLiteral queryExpression) {
        this.queryExpression = Objects.requireNonNull(queryExpression, "The query expression must not be null.");
    }

    public static SqlExpression of(StringLiteral queryExpression) {
        return new SqlExpression(queryExpression);
    }

    public static SqlExpression of(String queryExpression) {
        return new SqlExpression(StringLiteral.of(queryExpression));
    }

    public static SqlExpression of(QueryStatement<?> statement) {
        return new SqlExpression(StringLiteral.of(statement != null ? statement.toSql() : null));
    }

    public StringLiteral getQueryExpression() {
        return queryExpression;
    }

    @Override
    public void accept(FilterVisitor visitor) {
        visitor.visit(this);
    }
}
