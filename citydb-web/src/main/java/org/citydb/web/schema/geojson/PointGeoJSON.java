package org.citydb.web.schema.geojson;

import io.swagger.v3.oas.annotations.media.ArraySchema;

import java.math.BigDecimal;
import java.util.List;

public class PointGeoJSON extends GeometryGeoJSON {
    @ArraySchema(minItems = 2)
    private List<BigDecimal> coordinates;

    @Override
    public String getType() {
        return GeometryType.POINT.toString();
    }

    private PointGeoJSON(List<BigDecimal> coordinates) {
        this.coordinates = coordinates;
    }

    public static PointGeoJSON of(List<BigDecimal> coordinates) {
        return new PointGeoJSON(coordinates);
    }

    public List<BigDecimal> getCoordinates() {
        return coordinates;
    }

    public PointGeoJSON setCoordinates(List<BigDecimal> coordinates) {
        this.coordinates = coordinates;
        return this;
    }
}
