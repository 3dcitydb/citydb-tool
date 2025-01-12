/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
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

package org.citydb.query.filter.encoding;

import com.alibaba.fastjson2.JSONWriter;
import org.citydb.model.geometry.*;

import java.util.List;

public class GeoJSONWriter {
    private final JSONWriter jsonWriter;

    GeoJSONWriter(JSONWriter jsonWriter) {
        this.jsonWriter = jsonWriter;
    }

    void write(Geometry<?> geometry) {
        if (geometry != null) {
            switch (geometry.getGeometryType()) {
                case POINT -> writePoint((Point) geometry);
                case LINE_STRING -> writeLineString((LineString) geometry);
                case POLYGON -> writePolygon((Polygon) geometry);
                case MULTI_POINT -> writeMultiPoint((MultiPoint) geometry);
                case MULTI_LINE_STRING -> writeMultiLineString((MultiLineString) geometry);
                case MULTI_SURFACE, COMPOSITE_SURFACE, TRIANGULATED_SURFACE ->
                        writeMultiSurface((SurfaceCollection<?>) geometry);
            }
        }
    }

    private void writePoint(Point point) {
        startGeometry(JSONToken.POINT);
        jsonWriter.writeName(JSONToken.COORDINATES.value());
        jsonWriter.writeColon();
        writeCoordinate(point.getCoordinate());
        endGeometry();
    }

    private void writeLineString(LineString lineString) {
        startGeometry(JSONToken.LINESTRING);
        startCoordinates();
        writeCoordinates(lineString);
        endCoordinates();
        endGeometry();
    }

    private void writePolygon(Polygon polygon) {
        startGeometry(JSONToken.POLYGON);
        startCoordinates();
        writeCoordinates(polygon);
        endCoordinates();
        endGeometry();
    }

    private void writeMultiPoint(MultiPoint multiPoint) {
        startGeometry(JSONToken.MULTIPOINT);
        startCoordinates();

        if (!multiPoint.getPoints().isEmpty()) {
            for (int i = 0; i < multiPoint.getPoints().size(); i++) {
                if (i != 0) {
                    jsonWriter.writeComma();
                }
                writeCoordinate(multiPoint.getPoints().get(i).getCoordinate());
            }
        }

        endCoordinates();
        endGeometry();
    }

    private void writeMultiLineString(MultiLineString multiLineString) {
        startGeometry(JSONToken.MULTILINESTRING);
        startCoordinates();

        if (!multiLineString.getLineStrings().isEmpty()) {
            jsonWriter.startArray();
            for (int i = 0; i < multiLineString.getLineStrings().size(); i++) {
                if (i != 0) {
                    jsonWriter.writeComma();
                }
                writeCoordinates(multiLineString.getLineStrings().get(i));
            }
            jsonWriter.endArray();
        }

        endCoordinates();
        endGeometry();
    }

    private void writeMultiSurface(SurfaceCollection<?> surfaceCollection) {
        startGeometry(JSONToken.MULTIPOLYGON);
        startCoordinates();

        if (!surfaceCollection.getPolygons().isEmpty()) {
            jsonWriter.startArray();
            for (int i = 0; i < surfaceCollection.getPolygons().size(); i++) {
                if (i != 0) {
                    jsonWriter.writeComma();
                }
                writeCoordinates(surfaceCollection.getPolygons().get(i));
            }
            jsonWriter.endArray();
        }

        endCoordinates();
        endGeometry();
    }

    private void writeCoordinate(Coordinate coordinate) {
        jsonWriter.write(coordinate.getDimension() == 2 ?
                List.of(coordinate.getX(), coordinate.getY()) :
                List.of(coordinate.getX(), coordinate.getY(), coordinate.getZ()));
    }


    private void writeCoordinates(LineString lineString) {
        for (int i = 0; i < lineString.getPoints().size(); i++) {
            if (i != 0) {
                jsonWriter.writeComma();
            }
            writeCoordinate(lineString.getPoints().get(i));
        }
    }

    private void writeCoordinates(LinearRing linearRing) {
        for (int i = 0; i < linearRing.getPoints().size(); i++) {
            if (i != 0) {
                jsonWriter.writeComma();
            }
            writeCoordinate(linearRing.getPoints().get(i));
        }
    }

    private void writeCoordinates(Polygon polygon) {
        jsonWriter.startArray();
        writeCoordinates(polygon.getExteriorRing());
        jsonWriter.endArray();

        if (polygon.hasInteriorRings()) {
            jsonWriter.writeComma();
            for (int i = 0; i < polygon.getInteriorRings().size(); i++) {
                if (i != 0) {
                    jsonWriter.writeComma();
                }
                jsonWriter.startArray();
                writeCoordinates(polygon.getInteriorRings().get(i));
                jsonWriter.endArray();
            }
        }
    }

    private void startGeometry(JSONToken type) {
        jsonWriter.startObject();
        jsonWriter.writeName(JSONToken.TYPE.value());
        jsonWriter.writeColon();
        jsonWriter.writeString(type.value());
    }

    private void endGeometry() {
        jsonWriter.endObject();
    }

    private void startCoordinates() {
        jsonWriter.writeName(JSONToken.COORDINATES.value());
        jsonWriter.writeColon();
        jsonWriter.startArray();
    }

    private void endCoordinates() {
        jsonWriter.endArray();
    }
}
