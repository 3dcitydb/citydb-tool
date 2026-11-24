/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.encoding;

import org.citydb.model.common.Matrix3x4;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

import java.util.ArrayList;
import java.util.List;

public class Matrix3x4Reader extends ValueDeserializer<Matrix3x4> {
    @Override
    public Matrix3x4 deserialize(JsonParser parser, DeserializationContext context) {
        JsonNode array = context.readTree(parser);
        if (!array.isArray()) {
            return context.reportInputMismatch(this, "Expected an array of 12 numbers for a 3x4 matrix.");
        }

        List<Double> values = new ArrayList<>(array.size());
        for (JsonNode value : array) {
            if (!value.isNumber()) {
                return context.reportInputMismatch(this, "Matrix values must be numbers.");
            }

            values.add(value.doubleValue());
        }

        if (values.size() < 12) {
            return context.reportInputMismatch(this,
                    "A 3x4 matrix requires at least 12 numbers, but found %d.", values.size());
        }

        return Matrix3x4.ofRowMajor(values);
    }
}
