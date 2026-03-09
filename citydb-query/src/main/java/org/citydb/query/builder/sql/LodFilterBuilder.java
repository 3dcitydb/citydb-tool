/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.builder.sql;

import org.citydb.query.builder.QueryBuildException;
import org.citydb.query.lod.LodFilter;
import org.citydb.query.lod.LodFilterMode;
import org.citydb.sqlbuilder.operation.Exists;
import org.citydb.sqlbuilder.query.Select;

public class LodFilterBuilder {
    private final BuilderHelper helper;

    private LodFilterBuilder(BuilderHelper helper) {
        this.helper = helper;
    }

    static LodFilterBuilder of(BuilderHelper helper) {
        return new LodFilterBuilder(helper);
    }

    void build(LodFilter lodFilter, Select select, SqlContext context) throws QueryBuildException {
        select.where(Exists.of(
                helper.getDatabaseAdapter().getSchemaAdapter().getRecursiveLodQuery(
                        lodFilter.getLods(),
                        lodFilter.getMode() == LodFilterMode.AND,
                        lodFilter.getSearchDepth().orElse(Integer.MAX_VALUE),
                        context.getTable())));
    }
}
