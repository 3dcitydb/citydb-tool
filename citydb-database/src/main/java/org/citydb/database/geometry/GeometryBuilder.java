/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
 * Virtual City Systems, Germany
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
            Map<Integer, JSONObject> hierarchy = buildHierarchy(properties);
            if (!hierarchy.isEmpty()) {
                if (hierarchy.get(0).containsKey(Properties.JSON_KEY_PARENT)) {
                    throw new GeometryException("The geometry hierarchy lacks a root item.");
                }

                Map<Integer, Geometry<?>> parents = new HashMap<>(hierarchy.size());
                List<? extends Geometry<?>> primitives = getPrimitives(geometry);
                try {
                    for (Map.Entry<Integer, JSONObject> entry : hierarchy.entrySet()) {
                        Geometry<?> item = buildGeometry(entry.getValue(), parents, primitives);
                        parents.put(entry.getKey(), item);
                    }
                } catch (Exception e) {
                    throw new GeometryException("Failed to rebuild geometry hierarchy.", e);
                }

                if (!parents.isEmpty()) {
                    geometry = parents.get(0);
                }
            }

            processMetadata(geometry, properties);
        }

        return geometry;
    }

    private Geometry<?> buildGeometry(JSONObject item, Map<Integer, Geometry<?>> parents, List<? extends Geometry<?>> primitives) throws GeometryException {
        GeometryType type = GeometryType.fromDatabaseValue(item.getIntValue(Properties.JSON_KEY_TYPE, -1));
        if (type == null) {
            throw new GeometryException("Missing geometry type property.");
        }

        Geometry<?> parent = getParent(item, parents);
        Geometry<?> geometry = null;
        switch (type) {
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
            return geometry.setObjectId(item.getString(Properties.JSON_KEY_OBJECT_ID));
        } else {
            throw new GeometryException("Failed to parse geometry hierarchy item.");
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

    private Geometry<?> getParent(JSONObject item, Map<Integer, Geometry<?>> parents) throws GeometryException {
        Integer index = item.getInteger(Properties.JSON_KEY_PARENT);
        if (index != null) {
            Geometry<?> parent = parents.get(index);
            if (parent == null) {
                throw new GeometryException("Parent index out of bounds.");
            }

            return parent;
        } else {
            return null;
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

    private Map<Integer, JSONObject> buildHierarchy(JSONObject properties) {
        JSONArray items = properties.getJSONArray(Properties.JSON_KEY_HIERARCHY);
        if (items != null && !items.isEmpty()) {
            Map<Integer, JSONObject> hierarchy = new TreeMap<>();
            for (int i = 0; i < items.size(); i++) {
                Object item = items.get(i);
                if (item instanceof JSONObject) {
                    hierarchy.put(i, (JSONObject) item);
                }
            }

            return hierarchy;
        }

        return Collections.emptyMap();
    }

    private void processMetadata(Geometry<?> geometry, JSONObject properties) {
        if (geometry.getObjectId().isEmpty()) {
            geometry.setObjectId(properties.getString(Properties.JSON_KEY_OBJECT_ID));
        }

        if (properties.getBooleanValue(Properties.JSON_KEY_IS_2D, false)) {
            geometry.force2D();
        }
    }
}
