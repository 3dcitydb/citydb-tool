package org.citydb.web.schema;

import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.Point;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class PointGeoJSON extends GeometryGeoJSON {
    private List<BigDecimal> coordinates;

    @Override
    public String getType() {
        return "Point";
    }

    private PointGeoJSON(List<BigDecimal> coordinates) {
        this.coordinates = coordinates;
    }

    public static PointGeoJSON of(List<BigDecimal> coordinates) {
        return new PointGeoJSON(coordinates);
    }

    public static PointGeoJSON of(Point point) {
        return of(point.getCoordinate());
    }

    public static PointGeoJSON of(Coordinate coordinate) {
        List<BigDecimal> coordinates = new ArrayList<>();

        coordinates.add(BigDecimal.valueOf(coordinate.getX()));
        coordinates.add(BigDecimal.valueOf(coordinate.getY()));
        if (coordinate.getDimension() == 3) {
            coordinates.add(BigDecimal.valueOf(coordinate.getZ()));
        }

        return of(coordinates);
    }

    public List<BigDecimal> getCoordinates() {
        return coordinates;
    }
}
