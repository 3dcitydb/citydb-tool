/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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

    public static GeometryType fromDatabaseValue(int value) {
        return types.get(value);
    }

    public int getDatabaseValue() {
        return value;
    }

    public String getTypeName() {
        return typeName;
    }

    @Override
    public String toString() {
        return typeName;
    }
}
