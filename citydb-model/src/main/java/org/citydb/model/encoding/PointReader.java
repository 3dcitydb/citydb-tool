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
import org.citydb.model.geometry.Point;

import java.lang.reflect.Type;
import java.util.List;

public class PointReader implements ObjectReader<Point> {
    @Override
    public Point readObject(JSONReader jsonReader, Type type, Object o, long l) {
        if (jsonReader.isObject()) {
            JSONObject extent = jsonReader.readJSONObject();
            Object bounds = extent.get("coordinates");
            if (bounds instanceof JSONArray value) {
                List<Double> coordinates = value.stream()
                        .filter(Number.class::isInstance)
                        .map(Number.class::cast)
                        .map(Number::doubleValue)
                        .toList();
                if (coordinates.size() > 1) {
                    Point point = Point.of(Coordinate.of(coordinates.get(0), coordinates.get(1)));
                    SrsReference srs = extent.getObject("srs", SrsReference.class);
                    if (srs != null) {
                        point.setSRID(srs.getSRID().orElse(null))
                                .setSrsIdentifier(srs.getIdentifier().orElse(null));
                    }

                    return point;
                }
            }
        }

        return null;
    }
}
