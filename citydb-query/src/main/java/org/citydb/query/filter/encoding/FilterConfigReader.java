/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter.encoding;

import org.citydb.query.filter.Filter;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

public class FilterConfigReader extends ValueDeserializer<Filter> {
    @Override
    public Filter deserialize(JsonParser parser, DeserializationContext context) {
        JsonNode node = context.readTree(parser);
        try {
            if (node.isObject()) {
                return Filter.ofJSON(node);
            } else if (node.isString()) {
                return Filter.ofText(node.stringValue());
            } else {
                return context.reportInputMismatch(this,
                        "Expected a CQL2 text string or CQL2 JSON object as query filter but found '%s'.", node);
            }
        } catch (FilterParseException e) {
            return context.reportInputMismatch(this,
                    "Failed to parse query filter expression '%s': %s", node, e.getMessage());
        }
    }
}
