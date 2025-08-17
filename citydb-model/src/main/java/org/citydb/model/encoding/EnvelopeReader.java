/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
 * virtualcitysystems GmbH, Germany
 * https://vc.systems/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
