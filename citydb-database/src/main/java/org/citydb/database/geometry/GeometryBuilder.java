/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.geometry;

import org.citydb.core.json.Json;
import org.citydb.model.geometry.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GeometryBuilder {

    public Geometry<?> buildGeometry(Geometry<?> geometry, ObjectNode properties) throws GeometryException {
        if (geometry != null && properties != null) {
            if (properties.get(Properties.JSON_KEY_CHILDREN) instanceof ArrayNode children && !children.isEmpty()) {
                List<? extends Geometry<?>> primitives = getPrimitives(geometry);
                geometry = buildGeometry(properties, null, primitives);

                Map<Integer, Geometry<?>> geometries = new HashMap<>(children.size() + 1);
                geometries.put(-1, geometry);

                for (int i = 0; i < children.size(); i++) {
                    buildGeometry(i, children, geometries, primitives);
                }
            } else {
                GeometryType type = getType(properties);
                if (geometry.getGeometryType() != type) {
                    geometry = castGeometry(geometry, type);
                }
            }

            geometry.setObjectId(Json.getString(properties, Properties.JSON_KEY_OBJECT_ID));
            if (properties.path(Properties.JSON_KEY_IS_2D).asBoolean(false)) {
                geometry.force2D();
            }
        }

        return geometry;
    }

    private Geometry<?> buildGeometry(int childIndex, ArrayNode children, Map<Integer, Geometry<?>> geometries, List<? extends Geometry<?>> primitives) throws GeometryException {
        Geometry<?> geometry = geometries.get(childIndex);
        if (geometry == null) {
            if (!(children.get(childIndex) instanceof ObjectNode child)) {
                throw new GeometryException("Invalid JSON object in children array at index " + childIndex + ".");
            }

            Integer parentIndex = Json.getInteger(child, Properties.JSON_KEY_PARENT);
            if (parentIndex == null) {
                parentIndex = -1;
            } else if (parentIndex < 0 || parentIndex >= children.size()) {
                throw new GeometryException("Parent index out of bounds.");
            }

            Geometry<?> parent = geometries.get(parentIndex);
            if (parent == null) {
                parent = buildGeometry(parentIndex, children, geometries, primitives);
            }

            geometry = buildGeometry(child, parent, primitives)
                    .setObjectId(Json.getString(child, Properties.JSON_KEY_OBJECT_ID));
            geometries.put(childIndex, geometry);
        }

        return geometry;
    }

    private Geometry<?> buildGeometry(JsonNode item, Geometry<?> parent, List<? extends Geometry<?>> primitives) throws GeometryException {
        return switch (getType(item)) {
            case POINT -> {
                Point point = getPrimitive(item, primitives, Point.class);
                if (parent instanceof MultiPoint multiPoint) {
                    multiPoint.getPoints().add(point);
                }
                yield point;
            }
            case LINE_STRING -> {
                LineString lineString = getPrimitive(item, primitives, LineString.class);
                if (parent instanceof MultiLineString multiLineString) {
                    multiLineString.getLineStrings().add(lineString);
                }
                yield lineString;
            }
            case POLYGON -> {
                Polygon polygon = getPrimitive(item, primitives, Polygon.class);
                polygon.setReversed(item.path(Properties.JSON_KEY_IS_REVERSED).asBoolean(false));
                if (parent instanceof SurfaceCollection<?> surfaceCollection) {
                    surfaceCollection.getPolygons().add(polygon);
                }
                yield polygon;
            }
            case COMPOSITE_SURFACE -> parent instanceof Solid solid
                    ? solid.getShell()
                    : CompositeSurface.empty();
            case SOLID -> {
                Solid solid = Solid.empty();
                if (parent instanceof SolidCollection<?> solidCollection) {
                    solidCollection.getSolids().add(solid);
                }
                yield solid;
            }
            case MULTI_POINT -> MultiPoint.empty();
            case MULTI_LINE_STRING -> MultiLineString.empty();
            case MULTI_SURFACE -> MultiSurface.empty();
            case TRIANGULATED_SURFACE -> TriangulatedSurface.empty();
            case COMPOSITE_SOLID -> CompositeSolid.empty();
            case MULTI_SOLID -> MultiSolid.empty();
        };
    }

    private Geometry<?> castGeometry(Geometry<?> geometry, GeometryType type) throws GeometryException {
        try {
            return switch (type) {
                case POINT -> getPrimitives(geometry, Point.class).get(0);
                case MULTI_POINT -> MultiPoint.of(getPrimitives(geometry, Point.class));
                case LINE_STRING -> getPrimitives(geometry, LineString.class).get(0);
                case MULTI_LINE_STRING -> MultiLineString.of(getPrimitives(geometry, LineString.class));
                case POLYGON -> getPrimitives(geometry, Polygon.class).get(0);
                case MULTI_SURFACE -> MultiSurface.of(getPrimitives(geometry, Polygon.class));
                case TRIANGULATED_SURFACE -> TriangulatedSurface.of(getPrimitives(geometry, Polygon.class));
                case COMPOSITE_SURFACE -> CompositeSurface.of(getPrimitives(geometry, Polygon.class));
                case SOLID -> Solid.of(CompositeSurface.of(getPrimitives(geometry, Polygon.class)));
                case MULTI_SOLID -> geometry instanceof SolidCollection<?> solidCollection
                        ? MultiSolid.of(solidCollection.getSolids())
                        : MultiSolid.of(Solid.of(CompositeSurface.of(getPrimitives(geometry, Polygon.class))));
                case COMPOSITE_SOLID -> geometry instanceof SolidCollection<?> solidCollection
                        ? CompositeSolid.of(solidCollection.getSolids())
                        : CompositeSolid.of(Solid.of(CompositeSurface.of(getPrimitives(geometry, Polygon.class))));
            };
        } catch (Exception e) {
            throw new GeometryException("Failed to convert database geometry into a " +
                    type.getTypeName() + " geometry.");
        }
    }

    private List<? extends Geometry<?>> getPrimitives(Geometry<?> geometry) {
        return switch (geometry.getGeometryType()) {
            case POINT -> List.of((Point) geometry);
            case MULTI_POINT -> ((MultiPoint) geometry).getPoints();
            case LINE_STRING -> List.of((LineString) geometry);
            case MULTI_LINE_STRING -> ((MultiLineString) geometry).getLineStrings();
            case POLYGON -> List.of((Polygon) geometry);
            case COMPOSITE_SURFACE, MULTI_SURFACE, TRIANGULATED_SURFACE ->
                    ((SurfaceCollection<?>) geometry).getPolygons();
            case SOLID -> ((Solid) geometry).getShell().getPolygons();
            case COMPOSITE_SOLID, MULTI_SOLID -> ((SolidCollection<?>) geometry).getSolids().stream()
                    .map(solid -> solid.getShell().getPolygons())
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());
        };
    }

    @SuppressWarnings("unchecked")
    private <T extends Geometry<?>> List<T> getPrimitives(Geometry<?> geometry, Class<T> type) {
        List<? extends Geometry<?>> primitives = getPrimitives(geometry);
        if (!primitives.isEmpty() && type.isInstance(primitives.get(0))) {
            return (List<T>) primitives;
        } else {
            throw new ClassCastException();
        }
    }

    private <T extends Geometry<?>> T getPrimitive(JsonNode item, List<? extends Geometry<?>> primitives, Class<T> type) throws GeometryException {
        Integer index = Json.getInteger(item, Properties.JSON_KEY_GEOMETRY_INDEX);
        if (index == null) {
            throw new GeometryException("Missing geometry index.");
        } else if (index < 0 || index >= primitives.size()) {
            throw new GeometryException("Geometry index out of bounds.");
        }

        Geometry<?> primitive = primitives.get(index);
        if (type.isInstance(primitive)) {
            return type.cast(primitive);
        } else {
            throw new GeometryException("Invalid primitive type " + primitive.getGeometryType() + ".");
        }
    }

    private GeometryType getType(JsonNode item) throws GeometryException {
        GeometryType type = GeometryType.fromDatabaseValue(item.path(Properties.JSON_KEY_TYPE).asInt(-1));
        if (type != null) {
            return type;
        } else {
            throw new GeometryException("Missing geometry type property.");
        }
    }
}
