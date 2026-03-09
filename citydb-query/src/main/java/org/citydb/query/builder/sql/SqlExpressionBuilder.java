/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.builder.sql;

import org.citydb.database.util.SqlExpressionValidator;
import org.citydb.query.builder.QueryBuildException;
import org.citydb.query.builder.common.Type;
import org.citydb.query.filter.operation.SqlExpression;
import org.citydb.sqlbuilder.operation.In;
import org.citydb.sqlbuilder.util.PlainSql;

import java.util.List;

public class SqlExpressionBuilder {

    private SqlExpressionBuilder() {
    }

    static SqlExpressionBuilder newInstance() {
        return new SqlExpressionBuilder();
    }

    BuildResult build(SqlExpression sqlExpression, SqlContext context, boolean negate) throws QueryBuildException {
        String queryExpression = sqlExpression.getQueryExpression().getValue();
        SqlExpressionValidator.defaults().validate(queryExpression, invalid ->
                new QueryBuildException("Found illegal content in SQL expression: " + invalid));

        return BuildResult.of(In.of(context.getTable().column("id"),
                        List.of(PlainSql.of(queryExpression)),
                        negate),
                Type.BOOLEAN);
    }
}
