/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
