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
        startCoordinates(jsonWriter);
        jsonWriter.writeDouble(point.getCoordinate().getX());
        jsonWriter.writeComma();
        jsonWriter.writeDouble(point.getCoordinate().getY());
        if (point.getCoordinate().getDimension() == 3) {
            jsonWriter.writeComma();
            jsonWriter.writeDouble(point.getCoordinate().getZ());
        }
        endCoordinates(jsonWriter);
        endGeometry(jsonWriter);
    }
    
    private void writeLineString(LineString lineString) {
        startGeometry(JSONToken.LINESTRING);
        startCoordinates(jsonWriter);
        writeCoordinates(lineString);
        endCoordinates(jsonWriter);
        endGeometry(jsonWriter);
    }

    private void writePolygon(Polygon polygon) {
        startGeometry(JSONToken.POLYGON);
        startCoordinates(jsonWriter);
        writeCoordinates(polygon);
        endCoordinates(jsonWriter);
        endGeometry(jsonWriter);
    }

    private void writeMultiPoint(MultiPoint multiPoint) {
        startGeometry(JSONToken.MULTIPOINT);
        startCoordinates(jsonWriter);
        for (int i = 0; i < multiPoint.getPoints().size(); i++) {
            writeCoordinate(multiPoint.getPoints().get(i).getCoordinate());
            if (i < multiPoint.getPoints().size() - 1) {
                jsonWriter.writeComma();
            }
        }
        endCoordinates(jsonWriter);
        endGeometry(jsonWriter);
    }

    private void writeMultiLineString(MultiLineString multiLineString) {
        startGeometry(JSONToken.MULTILINESTRING);
        startCoordinates(jsonWriter);
        for (int i = 0; i < multiLineString.getLineStrings().size(); i++) {
            writeCoordinates(multiLineString.getLineStrings().get(i));
            if (i < multiLineString.getLineStrings().size() - 1) {
                jsonWriter.writeComma();
            }
        }
        endCoordinates(jsonWriter);
        endGeometry(jsonWriter);
    }

    private void writeMultiSurface(SurfaceCollection<?> surfaceCollection) {
        startGeometry(JSONToken.MULTIPOLYGON);
        startCoordinates(jsonWriter);
        for (int i = 0; i < surfaceCollection.getPolygons().size(); i++) {
            writeCoordinates(surfaceCollection.getPolygons().get(i));
            if (i < surfaceCollection.getPolygons().size() - 1) {
                jsonWriter.writeComma();
            }
        }
        endCoordinates(jsonWriter);
        endGeometry(jsonWriter);
    }

    private void writeCoordinate(Coordinate coordinate) {
        jsonWriter.startArray();
        jsonWriter.writeDouble(coordinate.getX());
        jsonWriter.writeComma();
        jsonWriter.writeDouble(coordinate.getY());
        if (coordinate.getDimension() == 3) {
            jsonWriter.writeComma();
            jsonWriter.writeDouble(coordinate.getZ());
        }
        jsonWriter.endArray();
    }


    private void writeCoordinates(LineString lineString) {
        for (int i = 0; i < lineString.getPoints().size(); i++) {
            writeCoordinate(lineString.getPoints().get(i));
            if (i < lineString.getPoints().size() - 1) {
                jsonWriter.writeComma();
            }
        }
    }

    private void writeCoordinates(LinearRing linearRing) {
        for (int i = 0; i < linearRing.getPoints().size(); i++) {
            writeCoordinate(linearRing.getPoints().get(i));
            if (i < linearRing.getPoints().size() - 1) {
                jsonWriter.writeComma();
            }
        }
    }

    private void writeCoordinates(Polygon polygon) {
        jsonWriter.startArray();
        writeCoordinates(polygon.getExteriorRing());
        jsonWriter.endArray();

        if (polygon.hasInteriorRings()) {
            jsonWriter.writeComma();
            for (int i = 0; i < polygon.getInteriorRings().size(); i++) {
                jsonWriter.startArray();
                writeCoordinates(polygon.getInteriorRings().get(i));
                jsonWriter.endArray();
                if (i < polygon.getInteriorRings().size() - 1) {
                    jsonWriter.writeComma();
                }
            }
        }
    }

    private void startGeometry(JSONToken type) {
        jsonWriter.startObject();
        jsonWriter.writeName(JSONToken.TYPE.value());
        jsonWriter.writeColon();
        jsonWriter.writeString(type.value());
    }

    private void endGeometry(JSONWriter jsonWriter) {
        jsonWriter.endObject();
    }

    private void startCoordinates(JSONWriter jsonWriter) {
        jsonWriter.writeName(JSONToken.COORDINATES.value());
        jsonWriter.writeColon();
        jsonWriter.startArray();
    }

    private void endCoordinates(JSONWriter jsonWriter) {
        jsonWriter.endArray();
    }
}
