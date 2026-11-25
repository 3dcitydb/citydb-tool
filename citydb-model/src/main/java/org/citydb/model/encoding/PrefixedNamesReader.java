/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.encoding;

import org.citydb.model.common.PrefixedName;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

import java.util.ArrayList;
import java.util.List;

public class PrefixedNamesReader extends ValueDeserializer<List<PrefixedName>> {
    @Override
    public List<PrefixedName> deserialize(JsonParser parser, DeserializationContext context) {
        JsonNode array = context.readTree(parser);
        if (!array.isArray()) {
            return context.reportInputMismatch(this, "Expected an array of names.");
        }

        List<PrefixedName> names = new ArrayList<>(array.size());
        for (JsonNode item : array) {
            JsonNode name = item.get("name");
            if (!item.isObject() || name == null || !name.isString()) {
                return context.reportInputMismatch(this, "Each entry requires a 'name' string.");
            }

            JsonNode namespace = item.get("namespace");
            names.add(PrefixedName.of(name.stringValue(),
                    namespace != null && !namespace.isNull() ? namespace.asString() : null));
        }

        return names;
    }
}
