package org.citydb.web.schema.geojson;

public enum GeometryType {
    POINT("Point"),
    POLYGON("Polygon"),
    MULTI_POLYGON("MultiPolygon");

    private final String value;

    GeometryType(String value) {
        this.value = value;
    }

    public String toValue() {
        return value;
    }

    public static GeometryType fromValue(String value) {
        for (GeometryType v : GeometryType.values()) {
            if (v.value.equals(value))
                return v;
        }

        return null;
    }

    @Override
    public String toString() {
        return value;
    }
}