/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
 * Virtual City Systems, Germany
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

package org.citydb.model.geometry;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public enum GeometryType {
    POINT(1, "Point"),
    MULTI_POINT(2, "MultiPoint"),
    LINE_STRING(3, "LineString"),
    MULTI_LINE_STRING(4, "MultiLineString"),
    POLYGON(5, "Polygon"),
    COMPOSITE_SURFACE(6, "CompositeSurface"),
    TRIANGULATED_SURFACE(7, "TriangulatedSurface"),
    MULTI_SURFACE(8, "MultiSurface"),
    SOLID(9, "Solid"),
    COMPOSITE_SOLID(10, "CompositeSolid"),
    MULTI_SOLID(11, "MultiSolid");

    private final static Map<Integer, GeometryType> types = new HashMap<>();
    private final int value;
    private final String typeName;

    static {
        Arrays.stream(values()).forEach(type -> types.put(type.value, type));
    }

    GeometryType(int value, String typeName) {
        this.value = value;
        this.typeName = typeName;
    }

    public int getDatabaseValue() {
        return value;
    }

    public String getTypeName() {
        return typeName;
    }

    public static GeometryType fromDatabaseValue(int value) {
        return types.get(value);
    }

    @Override
    public String toString() {
        return typeName;
    }
}
