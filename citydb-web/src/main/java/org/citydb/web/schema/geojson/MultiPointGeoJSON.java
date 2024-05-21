package org.citydb.web.schema.geojson;

import java.math.BigDecimal;
import java.util.List;

public class MultiPointGeoJSON extends GeometryGeoJSON {
    private List<List<BigDecimal>> coordinates;

    @Override
    public String getType() {
        return GeometryType.MULTI_POINT.toString();
    }

    private MultiPointGeoJSON(List<List<BigDecimal>> coordinates) {
        this.coordinates = coordinates;
    }

    public static MultiPointGeoJSON of(List<List<BigDecimal>> coordinates) {
        return new MultiPointGeoJSON(coordinates);
    }

    public List<List<BigDecimal>> getCoordinates() {
        return coordinates;
    }

    public MultiPointGeoJSON setCoordinates(List<List<BigDecimal>> coordinates) {
        this.coordinates = coordinates;
        return this;
    }
}
