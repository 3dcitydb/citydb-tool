package org.citydb.web.util;

import org.citydb.core.file.OutputFile;
import org.citydb.io.writer.FeatureWriter;
import org.citydb.io.writer.WriteException;
import org.citydb.io.writer.WriteOptions;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.*;
import org.citydb.model.geometry.GeometryType;
import org.citydb.model.property.Attribute;
import org.citydb.model.property.DataType;
import org.citydb.model.walker.ModelWalker;
import org.citydb.web.schema.geojson.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class GeoJSONWriter implements FeatureWriter {
    private final GeoJSONConverter geoJsonConverter;
    private final FeatureCollectionGeoJSON featureCollectionGeoJSON;

    public GeoJSONWriter() {
        geoJsonConverter = new GeoJSONConverter();
        featureCollectionGeoJSON = FeatureCollectionGeoJSON.newInstance();
    }

    @Override
    public void initialize(OutputFile file, WriteOptions options) throws WriteException {
        //
    }

    @Override
    public CompletableFuture<Boolean> write(Feature feature) throws WriteException {
        MultiSurface surfaces = MultiSurface.empty();
        feature.accept(new ModelWalker() {
            @Override
            public void visit(Polygon polygon) {
                surfaces.getPolygons().add(polygon);
            }
        });
        FeatureGeoJSON featureGeoJSON = FeatureGeoJSON.of(geoJsonConverter.convert(surfaces));
        Map<String, Object> properties = new HashMap<>();
        for (Attribute attribute : feature.getAttributes().getAll()) {
            if (attribute.getDataType().isPresent()) {
                String attrName = attribute.getName().getLocalName();
                switch (DataType.of(attribute.getDataType().get())) {
                    case INTEGER -> properties.put(attrName, attribute.getIntValue().orElse(null));
                    case DOUBLE -> properties.put(attrName, attribute.getDoubleValue().orElse(null));
                    case STRING, CODE -> properties.put(attrName, attribute.getStringValue().orElse(null));
                    case URI -> properties.put(attrName, attribute.getURI().orElse(null));
                    case TIMESTAMP -> properties.put(attrName, attribute.getTimeStamp().orElse(null));
                    case BOOLEAN -> properties.put(attrName, 1 == attribute.getIntValue().orElse(0L));
                }
            }
        }
        featureGeoJSON.setProperties(properties);
        featureCollectionGeoJSON.addFeature(featureGeoJSON);

        return CompletableFuture.completedFuture(true);
    }

    @Override
    public void cancel() {
        //
    }

    @Override
    public void close() throws WriteException {
        featureCollectionGeoJSON.getFeatures().clear();
    }

    public FeatureCollectionGeoJSON getFeatureCollectionGeoJSON() {
        return featureCollectionGeoJSON;
    }

    private static class GeoJSONConverter {

        public GeometryGeoJSON convert(Geometry<?> geometry){
            GeometryType geometryType = geometry.getGeometryType();

            return switch (geometryType) {
                case POINT -> convertPoint((Point) geometry);
                case MULTI_POINT -> convertMultiPoint((MultiPoint) geometry);
                case LINE_STRING -> convertLineString((LineString) geometry);
                case MULTI_LINE_STRING -> convertMultiLineString((MultiLineString) geometry);
                case POLYGON -> convertPolygon((Polygon) geometry);
                case MULTI_SURFACE, COMPOSITE_SURFACE, TRIANGULATED_SURFACE -> convertSurfaceCollection((SurfaceCollection<?>) geometry);
                case SOLID -> convertSolid((Solid) geometry);
                case MULTI_SOLID, COMPOSITE_SOLID -> convertSolidCollection((SolidCollection<?>) geometry);
            };
        }

        private PointGeoJSON convertPoint(Point point) {
            return PointGeoJSON.of(convertCoordinate(point.getCoordinate()));
        }

        private MultiPointGeoJSON convertMultiPoint(MultiPoint multiPoint) {
            List<Coordinate> coordinates = multiPoint.getPoints().stream()
                    .map(Point::getCoordinate).collect(Collectors.toList());

            return MultiPointGeoJSON.of(convertCoordinates(coordinates));
        }

        private LineStringGeoJSON convertLineString(LineString lineString) {
            return LineStringGeoJSON.of(convertCoordinates(lineString.getPoints()));
        }

        private MultiLineStringGeoJSON convertMultiLineString(MultiLineString multiLineString) {
            List<List<List<BigDecimal>>> coordinates = multiLineString.getLineStrings().stream()
                    .map(lineString -> convertCoordinates(lineString.getPoints())).collect(Collectors.toList());

            return MultiLineStringGeoJSON.of(coordinates);
        }

        private PolygonGeoJSON convertPolygon(Polygon polygon) {
            List<List<List<BigDecimal>>> coordinates = new ArrayList<>();

            coordinates.add(convertCoordinates(polygon.getExteriorRing().getPoints()));
            if (polygon.hasInteriorRings()) {
                for (LinearRing linearRing : polygon.getInteriorRings()) {
                    coordinates.add(convertCoordinates(linearRing.getPoints()));
                }
            }

            return PolygonGeoJSON.of(coordinates);
        }

        private MultiPolygonGeoJSON convertSurfaceCollection(SurfaceCollection<?> surfaceCollection) {
            return MultiPolygonGeoJSON.of(convertPolygons(surfaceCollection.getPolygons()));
        }

        private MultiPolygonGeoJSON convertSolid(Solid solid) {
            return MultiPolygonGeoJSON.of(convertPolygons(solid.getShell().getPolygons()));
        }

        private MultiPolygonGeoJSON convertSolidCollection(SolidCollection<?> solidCollection) {
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
                coordinates.add(convertPolygon(polygon).getCoordinates());
            }

            return coordinates;
        }
    }
}
