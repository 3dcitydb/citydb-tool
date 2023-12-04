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

public class PropertiesBuilder {

    public JSONObject buildProperties(Geometry<?> geometry) {
        if (geometry != null) {
            Hierarchy hierarchy = new Hierarchy();
            switch (geometry.getGeometryType()) {
                case POINT:
                    buildHierarchy((Point) geometry, -1, hierarchy);
                    break;
                case MULTI_POINT:
                    buildHierarchy((MultiPoint) geometry, hierarchy);
                    break;
                case LINE_STRING:
                    buildHierarchy((LineString) geometry, -1, hierarchy);
                    break;
                case MULTI_LINE_STRING:
                    buildHierarchy((MultiLineString) geometry, hierarchy);
                    break;
                case POLYGON:
                    buildHierarchy((Polygon) geometry, -1, hierarchy);
                    break;
                case MULTI_SURFACE:
                case TRIANGULATED_SURFACE:
                case COMPOSITE_SURFACE:
                    buildHierarchy((SurfaceCollection<?>) geometry, -1, hierarchy);
                    break;
                case SOLID:
                    buildHierarchy((Solid) geometry, -1, hierarchy);
                    break;
                case COMPOSITE_SOLID:
                case MULTI_SOLID:
                    buildHierarchy((SolidCollection<?>) geometry, hierarchy);
                    break;
            }

            return hierarchy.getRoot();
        }

        return null;
    }

    private void buildHierarchy(Point point, int parent, Hierarchy hierarchy) {
        hierarchy.add(point, parent);
    }

    private void buildHierarchy(MultiPoint multiPoint, Hierarchy hierarchy) {
        int index = hierarchy.add(multiPoint, -1);
        multiPoint.getPoints().forEach(point -> buildHierarchy(point, index, hierarchy));
    }

    private void buildHierarchy(LineString lineString, int parent, Hierarchy hierarchy) {
        hierarchy.add(lineString, parent);
    }

    private void buildHierarchy(MultiLineString multiLineString, Hierarchy hierarchy) {
        int index = hierarchy.add(multiLineString, -1);
        multiLineString.getLineStrings().forEach(lineString -> buildHierarchy(lineString, index, hierarchy));
    }

    private void buildHierarchy(Polygon polygon, int parent, Hierarchy hierarchy) {
        hierarchy.add(polygon, parent);
    }

    private void buildHierarchy(SurfaceCollection<?> surfaces, int parent, Hierarchy hierarchy) {
        int index = hierarchy.add(surfaces, parent);
        surfaces.getPolygons().forEach(polygon -> buildHierarchy(polygon, index, hierarchy));
    }

    private void buildHierarchy(Solid solid, int parent, Hierarchy hierarchy) {
        int index = hierarchy.add(solid, parent);
        buildHierarchy(solid.getShell(), index, hierarchy);
    }

    private void buildHierarchy(SolidCollection<?> solids, Hierarchy hierarchy) {
        int index = hierarchy.add(solids, -1);
        solids.getSolids().forEach(solid -> buildHierarchy(solid, index, hierarchy));
    }

    private static class Hierarchy {
        private final JSONObject root = new JSONObject();
        private final JSONArray children = new JSONArray();
        private int geometryIndex;

        private JSONObject getRoot() {
            if (!children.isEmpty()) {
                root.put(Properties.JSON_KEY_CHILDREN, children);
            }

            return root;
        }

        private int add(Geometry<?> geometry, int parent) {
            JSONObject item = (parent == -1 ? root : children.addObject())
                    .fluentPut(Properties.JSON_KEY_TYPE, geometry.getGeometryType().getDatabaseValue())
                    .fluentPut(Properties.JSON_KEY_OBJECT_ID, geometry.getOrCreateObjectId());

            if (parent == -1) {
                if (geometry.getVertexDimension() == 2) {
                    item.put(Properties.JSON_KEY_IS_2D, true);
                }
            } else {
                if (parent > 0) {
                    item.put(Properties.JSON_KEY_PARENT, parent - 1);
                }

                switch (geometry.getGeometryType()) {
                    case POINT:
                    case LINE_STRING:
                    case POLYGON:
                        item.put(Properties.JSON_KEY_GEOMETRY_INDEX, geometryIndex++);
                }
            }

            if (geometry instanceof Polygon && ((Polygon) geometry).isReversed()) {
                item.put(Properties.JSON_KEY_IS_REVERSED, true);
            }

            return children.size();
        }
    }
}
