/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
