/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.schema;

public class JoinTable {
    private final Table table;
    private final Join sourceJoin;
    private final Join targetJoin;

    JoinTable(Table table, Join sourceJoin, Join targetJoin) {
        this.table = table;
        this.sourceJoin = sourceJoin;
        this.targetJoin = targetJoin;
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
