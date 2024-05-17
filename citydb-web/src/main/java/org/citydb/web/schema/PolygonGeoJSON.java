package org.citydb.web.schema;

import io.swagger.v3.oas.annotations.media.ArraySchema;

import java.math.BigDecimal;
import java.util.List;

public class PolygonGeoJSON extends GeometryGeoJSON {
    @ArraySchema(minItems = 4)
    private List<List<List<BigDecimal>>> coordinates;

    @Override
    public GeometryType getType() {
        return GeometryType.Polygon;
    }

    private PolygonGeoJSON(List<List<List<BigDecimal>>> coordinates) {
        this.coordinates = coordinates;
    }

    public static PolygonGeoJSON of(List<List<List<BigDecimal>>> coordinates) {
        return new PolygonGeoJSON(coordinates);
    }

    public List<List<List<BigDecimal>>> getCoordinates() {
        return coordinates;
    }

    public PolygonGeoJSON setCoordinates(List<List<List<BigDecimal>>> coordinates) {
        this.coordinates = coordinates;
        return this;
    }
}
