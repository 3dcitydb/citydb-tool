/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.encoding;

import org.citydb.config.common.SrsReference;
import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.Point;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

public class PointReader extends ValueDeserializer<Point> {
    @Override
    public Point deserialize(JsonParser parser, DeserializationContext context) {
        JsonNode object = context.readTree(parser);
        if (!object.isObject()) {
            return context.reportInputMismatch(this, "Expected a point object.");
        }

        JsonNode coordinates = object.get("coordinates");
        if (coordinates == null || !coordinates.isArray() || coordinates.size() < 2
                || !coordinates.get(0).isNumber() || !coordinates.get(1).isNumber()) {
            return context.reportInputMismatch(this, "A point requires at least 2 numeric coordinates.");
        }

        Point point = Point.of(Coordinate.of(coordinates.get(0).doubleValue(), coordinates.get(1).doubleValue()));
        JsonNode srsNode = object.get("srs");
        if (srsNode != null && !srsNode.isNull()) {
            SrsReference srs = context.readTreeAsValue(srsNode, SrsReference.class);
            point.setSRID(srs.getSRID().orElse(null))
                    .setSrsIdentifier(srs.getIdentifier().orElse(null));
        }

        return point;
    }
}
