/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.encoding;

import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.writer.ObjectWriter;
import org.citydb.config.common.SrsReference;
import org.citydb.model.geometry.Point;

import java.lang.reflect.Type;
import java.util.List;

public class PointWriter implements ObjectWriter<Point> {
    @Override
    public void write(JSONWriter jsonWriter, Object o, Object o1, Type type, long l) {
        if (o instanceof Point point) {
            jsonWriter.startObject();
            jsonWriter.writeName("coordinates");
            jsonWriter.writeColon();
            jsonWriter.write(List.of(point.getCoordinate().getX(), point.getCoordinate().getY()));

            if (point.getSRID().isPresent() || point.getSrsIdentifier().isPresent()) {
                jsonWriter.writeName("srs");
                jsonWriter.writeColon();
                jsonWriter.writeAs(new SrsReference()
                                .setSRID(point.getSRID().orElse(null))
                                .setIdentifier(point.getSrsIdentifier().orElse(null)),
                        SrsReference.class);
            }

            jsonWriter.endObject();
        } else {
            jsonWriter.writeNull();
        }
    }
}
