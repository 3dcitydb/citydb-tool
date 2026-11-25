/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.encoding;

import org.citydb.config.common.SrsReference;
import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.Envelope;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

public class EnvelopeWriter extends ValueSerializer<Envelope> {
    @Override
    public void serialize(Envelope envelope, JsonGenerator generator, SerializationContext context) {
        Coordinate lowerCorner = envelope.getLowerCorner();
        Coordinate upperCorner = envelope.getUpperCorner();
        boolean is3D = envelope.getVertexDimension() != 2;

        generator.writeStartObject();
        generator.writeName("coordinates");
        generator.writeStartArray();
        generator.writeNumber(lowerCorner.getX());
        generator.writeNumber(lowerCorner.getY());
        if (is3D) {
            generator.writeNumber(lowerCorner.getZ());
        }

        generator.writeNumber(upperCorner.getX());
        generator.writeNumber(upperCorner.getY());
        if (is3D) {
            generator.writeNumber(upperCorner.getZ());
        }

        generator.writeEndArray();

        if (envelope.getSRID().isPresent() || envelope.getSrsIdentifier().isPresent()) {
            generator.writeName("srs");
            context.writeValue(generator, new SrsReference()
                    .setSRID(envelope.getSRID().orElse(null))
                    .setIdentifier(envelope.getSrsIdentifier().orElse(null)));
        }

        generator.writeEndObject();
    }
}
