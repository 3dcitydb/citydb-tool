package org.citydb.web.schema;

public abstract class GeometryGeoJSON {
    private String type;

    protected void setType(String type) {
        this.type = type;
    }

    public abstract String getType();
}
