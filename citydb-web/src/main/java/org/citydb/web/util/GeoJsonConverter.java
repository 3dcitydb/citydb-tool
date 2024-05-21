package org.citydb.web.util;

import org.citydb.model.geometry.*;
import org.citydb.model.geometry.GeometryType;
import org.citydb.web.schema.geojson.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
        return PointGeoJSON.of(convertCoordinate(point.getCoordinate()));
    }

    public MultiPointGeoJSON convert(MultiPoint multiPoint) {
        List<Coordinate> coordinates = multiPoint.getPoints().stream()
                .map(Point::getCoordinate).collect(Collectors.toList());

        return MultiPointGeoJSON.of(convertCoordinates(coordinates));
    }

    public LineStringGeoJSON convert(LineString lineString) {
        return LineStringGeoJSON.of(convertCoordinates(lineString.getPoints()));
    }

    public MultiLineStringGeoJSON convert(MultiLineString multiLineString) {
        List<List<List<BigDecimal>>> coordinates = multiLineString.getLineStrings().stream()
                .map(lineString -> convertCoordinates(lineString.getPoints())).collect(Collectors.toList());

        return MultiLineStringGeoJSON.of(coordinates);
    }

    public PolygonGeoJSON convert(Polygon polygon) {
        List<List<List<BigDecimal>>> coordinates = new ArrayList<>();

        coordinates.add(convertCoordinates(polygon.getExteriorRing().getPoints()));
        if (polygon.hasInteriorRings()) {
            for (LinearRing linearRing : polygon.getInteriorRings()) {
                coordinates.add(convertCoordinates(linearRing.getPoints()));
            }
        }

        return PolygonGeoJSON.of(coordinates);
    }

    public MultiPolygonGeoJSON convert(SurfaceCollection<?> surfaceCollection) {
        return MultiPolygonGeoJSON.of(convertPolygons(surfaceCollection.getPolygons()));
    }

    public MultiPolygonGeoJSON convert(Solid solid) {
        return MultiPolygonGeoJSON.of(convertPolygons(solid.getShell().getPolygons()));
    }

    public MultiPolygonGeoJSON convert(SolidCollection<?> solidCollection) {
        List<Polygon> polygons = solidCollection.getSolids().stream()
                .flatMap(solid -> solid.getShell().getPolygons().stream())
                .toList();

        return MultiPolygonGeoJSON.of(convertPolygons(polygons));
    }

    private List<BigDecimal> convertCoordinate(Coordinate point) {
        List<BigDecimal> coordinate = new ArrayList<>();

        coordinate.add(BigDecimal.valueOf(point.getX()));
        coordinate.add(BigDecimal.valueOf(point.getY()));
        if (point.getDimension() == 3) {
            coordinate.add(BigDecimal.valueOf(point.getZ()));
        }

        return coordinate;
    }

    private List<List<BigDecimal>> convertCoordinates(List<Coordinate> coordinates) {
        List<List<BigDecimal>> result = new ArrayList<>();

        for (Coordinate coordinate : coordinates) {
            result.add(convertCoordinate(coordinate));
        }

        return result;
    }

    private List<List<List<List<BigDecimal>>>> convertPolygons(List<Polygon> polygons) {
        List<List<List<List<BigDecimal>>>> coordinates = new ArrayList<>();

        for (Polygon polygon : polygons) {
            coordinates.add(convert(polygon).getCoordinates());
        }

        return coordinates;
    }
}
