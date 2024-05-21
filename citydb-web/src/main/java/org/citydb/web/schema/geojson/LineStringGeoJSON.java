package org.citydb.web.schema.geojson;

import io.swagger.v3.oas.annotations.media.ArraySchema;

import java.math.BigDecimal;
import java.util.List;

public class LineStringGeoJSON extends GeometryGeoJSON {
    @ArraySchema(minItems = 2)
    private List<List<BigDecimal>> coordinates;

    @Override
    public String getType() {
        return GeometryType.LINESTRING.toString();
    }

    private LineStringGeoJSON(List<List<BigDecimal>> coordinates) {
        this.coordinates = coordinates;
    }

    public static LineStringGeoJSON of(List<List<BigDecimal>> coordinates) {
        return new LineStringGeoJSON(coordinates);
    }

    public List<List<BigDecimal>> getCoordinates() {
        return coordinates;
    }

    public LineStringGeoJSON setCoordinates(List<List<BigDecimal>> coordinates) {
        this.coordinates = coordinates;
        return this;
    }
}
