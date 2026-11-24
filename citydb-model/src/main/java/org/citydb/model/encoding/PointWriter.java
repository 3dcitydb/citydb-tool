/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.encoding;

import org.citydb.config.common.SrsReference;
import org.citydb.model.geometry.Point;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

public class PointWriter extends ValueSerializer<Point> {
    @Override
    public void serialize(Point point, JsonGenerator generator, SerializationContext context) {
        generator.writeStartObject();
        generator.writeName("coordinates");
        generator.writeStartArray();
        generator.writeNumber(point.getCoordinate().getX());
        generator.writeNumber(point.getCoordinate().getY());
        generator.writeEndArray();

        if (point.getSRID().isPresent() || point.getSrsIdentifier().isPresent()) {
            generator.writeName("srs");
            context.writeValue(generator, new SrsReference()
                    .setSRID(point.getSRID().orElse(null))
                    .setIdentifier(point.getSrsIdentifier().orElse(null)));
        }

        generator.writeEndObject();
    }
}
