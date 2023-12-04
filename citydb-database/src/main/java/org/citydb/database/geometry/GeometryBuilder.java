/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
 * virtualcitysystems GmbH, Germany
 * https://vc.systems/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.citydb.database.geometry;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.citydb.model.geometry.*;

import java.util.*;
import java.util.stream.Collectors;

public class GeometryBuilder {

    public Geometry<?> buildGeometry(Geometry<?> geometry, JSONObject properties) throws GeometryException {
        if (geometry != null && properties != null) {
            JSONArray children = properties.getJSONArray(Properties.JSON_KEY_CHILDREN);
            if (children != null && !children.isEmpty()) {
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

            geometry.setObjectId(properties.getString(Properties.JSON_KEY_OBJECT_ID));
            if (properties.getBooleanValue(Properties.JSON_KEY_IS_2D, false)) {
                geometry.force2D();
            }
        }

        return geometry;
    }

    private Geometry<?> buildGeometry(int childIndex, JSONArray children, Map<Integer, Geometry<?>> geometries, List<? extends Geometry<?>> primitives) throws GeometryException {
        Geometry<?> geometry = geometries.get(childIndex);
        if (geometry == null) {
            JSONObject child = children.getJSONObject(childIndex);
            if (child == null) {
                throw new GeometryException("Invalid JSON object in children array at index " + childIndex + ".");
            }

            Integer parentIndex = child.getInteger(Properties.JSON_KEY_PARENT);
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
                    .setObjectId(child.getString(Properties.JSON_KEY_OBJECT_ID));
            geometries.put(childIndex, geometry);
        }

        return geometry;
    }

    private Geometry<?> buildGeometry(JSONObject item, Geometry<?> parent, List<? extends Geometry<?>> primitives) throws GeometryException {
        Geometry<?> geometry = null;
        switch (getType(item)) {
            case POINT:
                geometry = getPrimitive(item, primitives, Point.class);
                if (parent instanceof MultiPoint) {
                    ((MultiPoint) parent).getPoints().add((Point) geometry);
                }
                break;
            case LINE_STRING:
                geometry = getPrimitive(item, primitives, LineString.class);
                if (parent instanceof MultiLineString) {
                    ((MultiLineString) parent).getLineStrings().add((LineString) geometry);
                }
                break;
            case POLYGON:
                geometry = getPrimitive(item, primitives, Polygon.class);
                ((Polygon) geometry).setReversed(item.getBooleanValue(Properties.JSON_KEY_IS_REVERSED, false));
                if (parent instanceof SurfaceCollection<?>) {
                    ((SurfaceCollection<?>) parent).getPolygons().add((Polygon) geometry);
                }
                break;
            case COMPOSITE_SURFACE:
                geometry = parent instanceof Solid ?
                        ((Solid) parent).getShell() :
                        CompositeSurface.empty();
                break;
            case SOLID:
                geometry = Solid.empty();
                if (parent instanceof SolidCollection<?>) {
                    ((SolidCollection<?>) parent).getSolids().add((Solid) geometry);
                }
                break;
            case MULTI_POINT:
                geometry = MultiPoint.empty();
                break;
            case MULTI_LINE_STRING:
                geometry = MultiLineString.empty();
                break;
            case MULTI_SURFACE:
                geometry = MultiSurface.empty();
                break;
            case TRIANGULATED_SURFACE:
                geometry = TriangulatedSurface.empty();
                break;
            case COMPOSITE_SOLID:
                geometry = CompositeSolid.empty();
                break;
            case MULTI_SOLID:
                geometry = MultiSolid.empty();
                break;
        }

        if (geometry != null) {
            return geometry;
        } else {
            throw new GeometryException("Failed to parse geometry object.");
        }
    }

    private Geometry<?> castGeometry(Geometry<?> geometry, GeometryType type) throws GeometryException {
        try {
            switch (type) {
                case POINT:
                    return getPrimitives(geometry, Point.class).get(0);
                case MULTI_POINT:
                    return MultiPoint.of(getPrimitives(geometry, Point.class));
                case LINE_STRING:
                    return getPrimitives(geometry, LineString.class).get(0);
                case MULTI_LINE_STRING:
                    return MultiLineString.of(getPrimitives(geometry, LineString.class));
                case POLYGON:
                    return getPrimitives(geometry, Polygon.class).get(0);
                case MULTI_SURFACE:
                    return MultiSurface.of(getPrimitives(geometry, Polygon.class));
                case TRIANGULATED_SURFACE:
                    return TriangulatedSurface.of(getPrimitives(geometry, Polygon.class));
                case COMPOSITE_SURFACE:
                    return CompositeSurface.of(getPrimitives(geometry, Polygon.class));
                case SOLID:
                    return Solid.of(CompositeSurface.of(getPrimitives(geometry, Polygon.class)));
                case MULTI_SOLID:
                    return geometry instanceof SolidCollection<?> ?
                            MultiSolid.of(((SolidCollection<?>) geometry).getSolids()) :
                            MultiSolid.of(Solid.of(CompositeSurface.of(getPrimitives(geometry, Polygon.class))));
                case COMPOSITE_SOLID:
                    return geometry instanceof SolidCollection<?> ?
                            CompositeSolid.of(((SolidCollection<?>) geometry).getSolids()) :
                            CompositeSolid.of(Solid.of(CompositeSurface.of(getPrimitives(geometry, Polygon.class))));
                default:
                    return geometry;
            }
        } catch (Exception e) {
            throw new GeometryException("Failed to convert database geometry into a " +
                    type.getTypeName() + " geometry.");
        }
    }

    private List<? extends Geometry<?>> getPrimitives(Geometry<?> geometry) {
        switch (geometry.getGeometryType()) {
            case POINT:
                return List.of((Point) geometry);
            case MULTI_POINT:
                return ((MultiPoint) geometry).getPoints();
            case LINE_STRING:
                return List.of((LineString) geometry);
            case MULTI_LINE_STRING:
                return ((MultiLineString) geometry).getLineStrings();
            case POLYGON:
                return List.of((Polygon) geometry);
            case COMPOSITE_SURFACE:
            case MULTI_SURFACE:
            case TRIANGULATED_SURFACE:
                return ((SurfaceCollection<?>) geometry).getPolygons();
            case SOLID:
                return ((Solid) geometry).getShell().getPolygons();
            case COMPOSITE_SOLID:
            case MULTI_SOLID:
                return ((SolidCollection<?>) geometry).getSolids().stream()
                        .map(solid -> solid.getShell().getPolygons())
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList());
            default:
                return Collections.emptyList();
        }
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

    private <T extends Geometry<?>> T getPrimitive(JSONObject item, List<? extends Geometry<?>> primitives, Class<T> type) throws GeometryException {
        Integer index = item.getInteger(Properties.JSON_KEY_GEOMETRY_INDEX);
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

    private GeometryType getType(JSONObject item) throws GeometryException {
        GeometryType type = GeometryType.fromDatabaseValue(item.getIntValue(Properties.JSON_KEY_TYPE, -1));
        if (type != null) {
            return type;
        } else {
            throw new GeometryException("Missing geometry type property.");
        }
    }
}
