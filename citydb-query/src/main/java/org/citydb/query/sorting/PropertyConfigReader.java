/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.sorting;

import org.citydb.query.filter.encoding.FilterParseException;
import org.citydb.query.filter.encoding.FilterTextParser;
import org.citydb.query.filter.literal.PropertyRef;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

public class PropertyConfigReader extends ValueDeserializer<PropertyRef> {
    @Override
    public PropertyRef deserialize(JsonParser parser, DeserializationContext context) {
        JsonNode node = context.readTree(parser);
        if (!node.isString()) {
            return context.reportInputMismatch(this,
                    "Expected a property reference string but found '%s'.", node);
        }

        try {
            PropertyRef propertyRef = FilterTextParser.newInstance().parse(node.stringValue(), PropertyRef.class);
            if (propertyRef == null) {
                return context.reportInputMismatch(this,
                        "Failed to parse '%s' as property reference.", node.stringValue());
            }

            return propertyRef;
        } catch (FilterParseException e) {
            return context.reportInputMismatch(this,
                    "Failed to parse property reference '%s': %s", node.stringValue(), e.getMessage());
        }
    }
}
