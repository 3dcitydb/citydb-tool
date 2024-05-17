package org.citydb.web.schema.geojson;

import java.math.BigDecimal;
import java.util.List;

public class MultiPolygonGeoJSON extends GeometryGeoJSON {

    private List<List<List<List<BigDecimal>>>> coordinates;

    @Override
    public String getType() {
        return GeometryType.MULTI_POLYGON.toString();
    }

    private MultiPolygonGeoJSON(List<List<List<List<BigDecimal>>>> coordinates) {
        this.coordinates = coordinates;
    }

    public static MultiPolygonGeoJSON of(List<List<List<List<BigDecimal>>>> coordinates) {
        return new MultiPolygonGeoJSON(coordinates);
    }

    public List<List<List<List<BigDecimal>>>> getCoordinates() {
        return coordinates;
    }

    public MultiPolygonGeoJSON setCoordinates(List<List<List<List<BigDecimal>>>> coordinates) {
        this.coordinates = coordinates;
        return this;
    }
}
