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

package org.citydb.query.builder.sql;

import org.citydb.database.util.SqlExpressionValidator;
import org.citydb.query.builder.QueryBuildException;
import org.citydb.query.builder.common.Type;
import org.citydb.query.filter.operation.SqlExpression;
import org.citydb.sqlbuilder.operation.In;
import org.citydb.sqlbuilder.util.PlainText;

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
                        List.of(PlainText.of(queryExpression)),
                        negate),
                Type.BOOLEAN);
    }
}
