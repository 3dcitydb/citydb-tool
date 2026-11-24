/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.index;

import org.citydb.core.json.JsonMapping;
import org.citydb.database.schema.Index;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Locale;
import java.util.Map;

public class IndexStatusBuilder {

    private IndexStatusBuilder() {
    }

    public static ObjectNode build(Map<Index, Boolean> indexes) {
        ObjectNode status = JsonMapping.mapper().createObjectNode();
        status.set("indexes", buildIndexes(indexes));
        return status;
    }

    private static ArrayNode buildIndexes(Map<Index, Boolean> indexes) {
        ArrayNode array = JsonMapping.mapper().createArrayNode();
        indexes.forEach((index, status) -> {
            ObjectNode item = array.addObject().put("table", index.getTable());
            ArrayNode columns = item.putArray("columns");
            index.getColumns().forEach(columns::add);
            item.put("type", index.getType().toString().toLowerCase(Locale.ROOT))
                    .put("name", index.getName())
                    .put("status", status ? "on" : "off");
        });

        return array;
    }
}
