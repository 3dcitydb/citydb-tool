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

import org.citydb.query.limit.CountLimit;
import org.citydb.sqlbuilder.literal.IntegerLiteral;
import org.citydb.sqlbuilder.query.OrderBy;
import org.citydb.sqlbuilder.query.Select;
import org.citydb.sqlbuilder.schema.Column;

public class CountLimitBuilder {

    private CountLimitBuilder() {
    }

    static CountLimitBuilder newInstance() {
        return new CountLimitBuilder();
    }

    void build(CountLimit countLimit, Select select, SqlContext context) {
        IntegerLiteral limit = countLimit.getLimit().map(IntegerLiteral::of).orElse(null);
        IntegerLiteral startIndex = countLimit.getStartIndex().map(IntegerLiteral::of).orElse(null);

        if (limit != null || startIndex != null) {
            select.offset(startIndex, limit);

            if (select.getOrderBy().stream()
                    .map(OrderBy::getSortExpression)
                    .filter(Column.class::isInstance)
                    .map(Column.class::cast)
                    .noneMatch(column -> column.getTable() == context.getTable()
                            && column.getName().equalsIgnoreCase("id"))) {
                select.orderBy(context.getTable().column("id"));
            }
        }
    }
}
