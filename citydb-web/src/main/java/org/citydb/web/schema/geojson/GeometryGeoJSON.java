package org.citydb.web.schema.geojson;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(anyOf =  {
        PointGeoJSON.class,
        PolygonGeoJSON.class
})
public abstract class GeometryGeoJSON {
    private GeometryType type;

    protected void setType(GeometryType type) {
        this.type = type;
    }

    public abstract String getType();
}
