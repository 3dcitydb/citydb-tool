/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.encoding;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.reader.ObjectReader;
import org.citydb.config.common.SrsReference;
import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.Envelope;

import java.lang.reflect.Type;
import java.util.List;

public class EnvelopeReader implements ObjectReader<Envelope> {
    @Override
    public Envelope readObject(JSONReader jsonReader, Type type, Object o, long l) {
        if (jsonReader.isObject()) {
            JSONObject object = jsonReader.readJSONObject();
            Object bounds = object.get("coordinates");
            if (bounds instanceof JSONArray value) {
                Envelope envelope = null;
                List<Double> coordinates = value.stream()
                        .filter(Number.class::isInstance)
                        .map(Number.class::cast)
                        .map(Number::doubleValue)
                        .toList();
                if (coordinates.size() == 4) {
                    envelope = Envelope.of(
                            Coordinate.of(coordinates.get(0), coordinates.get(1)),
                            Coordinate.of(coordinates.get(2), coordinates.get(3)));
                } else if (coordinates.size() == 6) {
                    envelope = Envelope.of(
                            Coordinate.of(coordinates.get(0), coordinates.get(1), coordinates.get(2)),
                            Coordinate.of(coordinates.get(3), coordinates.get(4), coordinates.get(5)));
                }

                if (envelope != null) {
                    SrsReference srs = object.getObject("srs", SrsReference.class);
                    if (srs != null) {
                        envelope.setSRID(srs.getSRID().orElse(null))
                                .setSrsIdentifier(srs.getIdentifier().orElse(null));
                    }

                    return envelope;
                }
            }
        }

        return null;
    }
}
