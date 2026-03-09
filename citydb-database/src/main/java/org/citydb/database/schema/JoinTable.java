/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.schema;

import com.alibaba.fastjson2.JSONObject;

public class JoinTable {
    private final Table table;
    private final Join sourceJoin;
    private final Join targetJoin;

    private JoinTable(Table table, Join sourceJoin, Join targetJoin) {
        this.table = table;
        this.sourceJoin = sourceJoin;
        this.targetJoin = targetJoin;
    }

    static JoinTable of(JSONObject object) throws SchemaException {
        String tableName = object.getString("table");
        JSONObject sourceJoin = object.getJSONObject("sourceJoin");
        JSONObject targetJoin = object.getJSONObject("targetJoin");

        if (tableName == null) {
            throw new SchemaException("No table defined for the join table.");
        } else if (sourceJoin == null) {
            throw new SchemaException("No source join defined for the join table.");
        } else if (targetJoin == null) {
            throw new SchemaException("No target join defined for the join table.");
        }

        Table table = Table.of(tableName);
        if (table == null) {
            throw new SchemaException("The join table " + tableName + " is not supported.");
        }

        return new JoinTable(table, Join.of(sourceJoin), Join.of(targetJoin));
    }

    public Table getTable() {
        return table;
    }

    public Join getSourceJoin() {
        return sourceJoin;
    }

    public Join getTargetJoin() {
        return targetJoin;
    }

    void postprocess(Joinable joinable, SchemaMapping schemaMapping) {
        sourceJoin.postprocess(joinable, schemaMapping);
        targetJoin.postprocess(joinable, schemaMapping);
    }
}
