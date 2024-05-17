package org.citydb.web.schema;

public enum GeometryType {
    Point("Point"),
    Polygon("Polygon");

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