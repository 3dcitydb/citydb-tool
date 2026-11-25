/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.encoding;

import org.citydb.config.common.SrsReference;
import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.Envelope;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

import java.util.ArrayList;
import java.util.List;

public class EnvelopeReader extends ValueDeserializer<Envelope> {
    @Override
    public Envelope deserialize(JsonParser parser, DeserializationContext context) {
        JsonNode object = context.readTree(parser);
        if (!object.isObject()) {
            return context.reportInputMismatch(this, "Expected an envelope object.");
        }

        List<Double> coordinates = new ArrayList<>();
        JsonNode array = object.get("coordinates");
        if (array != null && array.isArray()) {
            for (JsonNode value : array) {
                if (!value.isNumber()) {
                    return context.reportInputMismatch(this, "Envelope coordinates must be numbers.");
                }

                coordinates.add(value.doubleValue());
            }
        }

        Envelope envelope;
        if (coordinates.size() == 4) {
            envelope = Envelope.of(
                    Coordinate.of(coordinates.get(0), coordinates.get(1)),
                    Coordinate.of(coordinates.get(2), coordinates.get(3)));
        } else if (coordinates.size() == 6) {
            envelope = Envelope.of(
                    Coordinate.of(coordinates.get(0), coordinates.get(1), coordinates.get(2)),
                    Coordinate.of(coordinates.get(3), coordinates.get(4), coordinates.get(5)));
        } else {
            return context.reportInputMismatch(this,
                    "An envelope requires 4 or 6 coordinates, but found %d.", coordinates.size());
        }

        JsonNode srsNode = object.get("srs");
        if (srsNode != null && !srsNode.isNull()) {
            SrsReference srs = context.readTreeAsValue(srsNode, SrsReference.class);
            envelope.setSRID(srs.getSRID().orElse(null))
                    .setSrsIdentifier(srs.getIdentifier().orElse(null));
        }

        return envelope;
    }
}
