package org.citydb.web.schema.geojson;

import java.math.BigDecimal;
import java.util.List;

public class MultiLineStringGeoJSON extends GeometryGeoJSON {
    private List<List<List<BigDecimal>>> coordinates;

    @Override
    public String getType() {
        return GeometryType.MULTI_LINESTRING.toString();
    }

    private MultiLineStringGeoJSON(List<List<List<BigDecimal>>> coordinates) {
        this.coordinates = coordinates;
    }

    public static MultiLineStringGeoJSON of(List<List<List<BigDecimal>>> coordinates) {
        return new MultiLineStringGeoJSON(coordinates);
    }

    public List<List<List<BigDecimal>>> getCoordinates() {
        return coordinates;
    }

    public MultiLineStringGeoJSON setCoordinates(List<List<List<BigDecimal>>> coordinates) {
        this.coordinates = coordinates;
        return this;
    }
}
