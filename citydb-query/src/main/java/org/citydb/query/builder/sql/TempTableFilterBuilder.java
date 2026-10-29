/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.builder.sql;

import org.citydb.query.temptable.TempTableFilter;
import org.citydb.sqlbuilder.literal.IntegerLiteral;
import org.citydb.sqlbuilder.operation.Exists;
import org.citydb.sqlbuilder.operation.Operators;
import org.citydb.sqlbuilder.query.Select;
import org.citydb.sqlbuilder.schema.Table;

import java.util.List;

public class TempTableFilterBuilder {

    private TempTableFilterBuilder() {
    }

    static TempTableFilterBuilder newInstance() {
        return new TempTableFilterBuilder();
    }

    void build(List<TempTableFilter> tempTableFilters, Select select, SqlContext context) {
        tempTableFilters.forEach(tempTableFilter -> build(tempTableFilter, select, context));
    }

    private void build(TempTableFilter tempTableFilter, Select select, SqlContext context) {
        Table tempTable = tempTableFilter.getTempTable().getTable();

        select.where(Exists.of(Select.newInstance()
                .select(IntegerLiteral.of(1))
                .from(tempTable)
                .where(Operators.eq(tempTable.column(tempTableFilter.getTempTableColumn()),
                        context.getTable().column(tempTableFilter.getFeatureColumn())))));
    }
}
