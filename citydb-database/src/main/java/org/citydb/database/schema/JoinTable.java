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
