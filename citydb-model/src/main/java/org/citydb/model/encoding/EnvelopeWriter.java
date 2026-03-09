/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.encoding;

import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.writer.ObjectWriter;
import org.citydb.config.common.SrsReference;
import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.Envelope;

import java.lang.reflect.Type;
import java.util.List;

public class EnvelopeWriter implements ObjectWriter<Envelope> {
    @Override
    public void write(JSONWriter jsonWriter, Object o, Object o1, Type type, long l) {
        if (o instanceof Envelope envelope) {
            Coordinate lowerCorner = envelope.getLowerCorner();
            Coordinate upperCorner = envelope.getUpperCorner();

            jsonWriter.startObject();
            jsonWriter.writeName("coordinates");
            jsonWriter.writeColon();
            jsonWriter.write(envelope.getVertexDimension() == 2 ?
                    List.of(lowerCorner.getX(), lowerCorner.getY(),
                            upperCorner.getX(), upperCorner.getY()) :
                    List.of(lowerCorner.getX(), lowerCorner.getY(), lowerCorner.getZ(),
                            upperCorner.getX(), upperCorner.getY(), upperCorner.getZ()));

            if (envelope.getSRID().isPresent() || envelope.getSrsIdentifier().isPresent()) {
                jsonWriter.writeName("srs");
                jsonWriter.writeColon();
                jsonWriter.writeAs(new SrsReference()
                                .setSRID(envelope.getSRID().orElse(null))
                                .setIdentifier(envelope.getSrsIdentifier().orElse(null)),
                        SrsReference.class);
            }

            jsonWriter.endObject();
        } else {
            jsonWriter.writeNull();
        }
    }
}
