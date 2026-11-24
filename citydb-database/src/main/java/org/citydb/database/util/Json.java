/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.util;

import tools.jackson.core.StreamWriteFeature;
import tools.jackson.core.TokenStreamFactory;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

public final class Json {
    private static final JsonMapper MAPPER = JsonMapper.builder(JsonFactory.builder()
                    .disable(TokenStreamFactory.Feature.CANONICALIZE_PROPERTY_NAMES)
                    .build())
            .enable(StreamWriteFeature.USE_FAST_DOUBLE_WRITER)
            .build();

    private Json() {
    }

    public static JsonMapper mapper() {
        return MAPPER;
    }

    public static ObjectNode objectNode() {
        return MAPPER.createObjectNode();
    }

    public static ArrayNode arrayNode() {
        return MAPPER.createArrayNode();
    }

    public static ArrayNode arrayOf(List<?> values) {
        ArrayNode array = MAPPER.createArrayNode();
        for (Object value : values) {
            switch (value) {
                case null -> array.addNull();
                case Boolean bool -> array.add(bool);
                case Integer intValue -> array.add(intValue);
                case Long longValue -> array.add(longValue);
                case Double doubleValue -> array.add(doubleValue);
                case Float floatValue -> array.add(floatValue);
                default -> array.add(value.toString());
            }
        }

        return array;
    }

    public static ObjectNode readObject(String content) {
        return content != null && MAPPER.readTree(content) instanceof ObjectNode object ? object : null;
    }

    public static ArrayNode readArray(String content) {
        return content != null && MAPPER.readTree(content) instanceof ArrayNode array ? array : null;
    }

    public static String write(JsonNode node) {
        return node != null ? MAPPER.writeValueAsString(node) : null;
    }

    public static String getString(JsonNode node, String name) {
        JsonNode value = node.get(name);
        return value != null && !value.isNull() ? value.asString() : null;
    }

    public static ObjectNode getObject(JsonNode node, String name) {
        return node.get(name) instanceof ObjectNode object ? object : null;
    }

    public static ArrayNode getArray(JsonNode node, String name) {
        return node.get(name) instanceof ArrayNode array ? array : null;
    }

    public static Integer getInteger(JsonNode node, String name) {
        JsonNode value = node.get(name);
        return value != null && value.isNumber() ? value.intValue() : null;
    }

    public static Object toJava(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        } else if (node.isBoolean()) {
            return node.booleanValue();
        } else if (node.isInt()) {
            return node.intValue();
        } else if (node.isLong()) {
            return node.longValue();
        } else if (node.isFloatingPointNumber()) {
            return node.doubleValue();
        } else if (node.isValueNode()) {
            return node.asString();
        } else {
            return Json.write(node);
        }
    }
}
