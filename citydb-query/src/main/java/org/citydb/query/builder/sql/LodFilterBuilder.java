/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.builder.sql;

import org.citydb.database.adapter.SchemaAdapter;
import org.citydb.query.lod.LodFilter;
import org.citydb.query.lod.LodPlan;
import org.citydb.query.lod.LodSearch;
import org.citydb.sqlbuilder.operation.BooleanExpression;
import org.citydb.sqlbuilder.operation.Exists;
import org.citydb.sqlbuilder.query.Select;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class LodFilterBuilder {
    private final BuilderHelper helper;

    private LodFilterBuilder(BuilderHelper helper) {
        this.helper = helper;
    }

    static LodFilterBuilder of(BuilderHelper helper) {
        return new LodFilterBuilder(helper);
    }

    void build(LodFilter lodFilter, LodPlan plan, Select select, SqlContext context) {
        SchemaAdapter schemaAdapter = helper.getDatabaseAdapter().getSchemaAdapter();
        List<BooleanExpression> topDown = new ArrayList<>();
        for (Set<String> group : lodFilter.getGroups()) {
            if (plan.get(group) == LodSearch.BOTTOM_UP) {
                select.where(context.getTable()
                        .column("id")
                        .in(schemaAdapter.getLodHelper().getBottomUpQuery(group)));
            } else {
                topDown.add(Exists.of(schemaAdapter.getLodHelper().getTopDownQuery(group, context.getTable())));
            }
        }

        // add the top-down conditions last, so that the cheaper bottom-up conditions are evaluated first
        topDown.forEach(select::where);
    }
}
