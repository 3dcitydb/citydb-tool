package org.citydb.web.util;

import org.citydb.model.geometry.*;
import org.citydb.web.schema.geojson.GeometryGeoJSON;
import org.citydb.web.schema.geojson.MultiPolygonGeoJSON;
import org.citydb.web.schema.geojson.PointGeoJSON;
import org.citydb.web.schema.geojson.PolygonGeoJSON;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class GeoJsonConverter {

    public GeometryGeoJSON convert(Geometry<?> geometry){
        GeometryType geometryType = geometry.getGeometryType();

        return switch (geometryType) {
            case POINT -> convert((Point) geometry);
            case MULTI_POINT -> convert((MultiPoint) geometry);
            case LINE_STRING -> convert((LineString) geometry);
            case MULTI_LINE_STRING -> convert((MultiLineString) geometry);
            case POLYGON -> convert((Polygon) geometry);
            case MULTI_SURFACE, COMPOSITE_SURFACE, TRIANGULATED_SURFACE -> convert((SurfaceCollection<?>) geometry);
            case SOLID -> convert((Solid) geometry);
            case MULTI_SOLID, COMPOSITE_SOLID -> convert((SolidCollection<?>) geometry);
        };
    }

    public PointGeoJSON convert(Point point) {
        return PointGeoJSON.of(convert(point.getCoordinate()));
    }

    public PointGeoJSON convert(MultiPoint multiPoint) {
        return null;
    }

    public PointGeoJSON convert(LineString lineString) {
        return null;
    }

    public PointGeoJSON convert(MultiLineString multiLineString) {
        return null;
    }

    public PolygonGeoJSON convert(Polygon polygon) {
        List<List<List<BigDecimal>>> coordinates = new ArrayList<>();
        coordinates.add(convert(polygon.getExteriorRing()));
        if (polygon.hasInteriorRings()) {
            List<LinearRing> linearRings = polygon.getInteriorRings();
            for (int i = 0; i < linearRings.size(); i++) {
                coordinates.add(convert(polygon.getExteriorRing()));
            }
        }

        return PolygonGeoJSON.of(coordinates);
    }

    public MultiPolygonGeoJSON convert(SurfaceCollection<?> surfaceCollection) {
        List<List<List<List<BigDecimal>>>> coordinates = new ArrayList<>();
        for (Polygon polygon : surfaceCollection.getPolygons()) {
            coordinates.add(convert(polygon).getCoordinates());
        }

        return MultiPolygonGeoJSON.of(coordinates);
    }

    public PointGeoJSON convert(Solid solid) {
        return null;
    }

    public PointGeoJSON convert(SolidCollection<?> solidCollection) {
        return null;
    }

    private List<BigDecimal> convert(Coordinate point) {
        List<BigDecimal> coordinate = new ArrayList<>();
        coordinate.add(BigDecimal.valueOf(point.getX()));
        coordinate.add(BigDecimal.valueOf(point.getY()));
        if (point.getDimension() == 3) {
            coordinate.add(BigDecimal.valueOf(point.getZ()));
        }

        return coordinate;
    }

    private List<List<BigDecimal>> convert(LinearRing ring) {
        List<List<BigDecimal>> coordinates = new ArrayList<>();
        for (Coordinate point : ring.getPoints()) {
            coordinates.add(convert(point));
        }

        return coordinates;
    }
}
