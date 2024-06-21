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

package org.citydb.query.builder.sql;

import org.citydb.query.builder.QueryBuildException;
import org.citydb.query.builder.common.Type;
import org.citydb.query.filter.operation.SqlExpression;
import org.citydb.sqlbuilder.operation.In;
import org.citydb.sqlbuilder.util.PlainText;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SqlExpressionBuilder {
    private final Matcher matcher = Pattern.compile("").matcher("");
    private final List<Pattern> illegalContents = List.of(
            Pattern.compile("(delete_.+?\\(.*?\\))"),
            Pattern.compile("(cleanup_.+?\\(.*?\\))"),
            Pattern.compile("(change_.+?\\(.*?\\))"),
            Pattern.compile("(set_.+?\\(.*?\\))"),
            Pattern.compile("(update_.+?\\(.*?\\))")
    );

    private SqlExpressionBuilder() {
    }

    static SqlExpressionBuilder newInstance() {
        return new SqlExpressionBuilder();
    }

    BuildResult build(SqlExpression sqlExpression, SqlContext context, boolean negate) throws QueryBuildException {
        String queryExpression = sqlExpression.getQueryExpression().getValue();

        for (Pattern pattern : illegalContents) {
            matcher.reset(queryExpression).usePattern(pattern);
            if (matcher.find()) {
                throw new QueryBuildException("Found illegal content in SQL expression: " + matcher.group(1));
            }
        }

        return BuildResult.of(In.of(context.getTable().column("id"),
                        List.of(PlainText.of(queryExpression)),
                        negate),
                Type.BOOLEAN);
    }
}
