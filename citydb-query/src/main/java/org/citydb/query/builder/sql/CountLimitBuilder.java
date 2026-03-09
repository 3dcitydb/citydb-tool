/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.builder.sql;

import org.citydb.config.common.CountLimit;
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
