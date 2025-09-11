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

package org.citydb.cli.index;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.citydb.database.schema.Index;

import java.util.Locale;
import java.util.Map;

public class IndexStatusBuilder {

    private IndexStatusBuilder() {
    }

    public static JSONObject build(Map<Index, Boolean> indexes) {
        return new JSONObject().fluentPut("indexes", buildIndexes(indexes));
    }

    private static JSONArray buildIndexes(Map<Index, Boolean> indexes) {
        JSONArray array = new JSONArray();
        indexes.forEach((index, status) -> array.add(new JSONObject().fluentPut("table", index.getTable())
                .fluentPut("columns", index.getColumns())
                .fluentPut("type", index.getType().toString().toLowerCase(Locale.ROOT))
                .fluentPut("name", index.getName())
                .fluentPut("status", status ? "on" : "off")));

        return array;
    }
}
