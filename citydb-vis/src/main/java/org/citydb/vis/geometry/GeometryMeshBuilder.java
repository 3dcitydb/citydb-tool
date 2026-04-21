/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.geometry;

import org.citydb.model.appearance.TextureCoordinate;
import org.citydb.model.geometry.Geometry;
import org.citydb.model.geometry.LinearRing;
import org.citydb.model.property.GeometryProperty;
import org.citydb.vis.util.GeoTransform;

import java.util.List;
import java.util.Map;

/**
 * Converts a feature's geometry properties into a single {@link TriangleMesh}
 * via polygon triangulation and geometry-level post-processing
 * (T-junction crack resolution, duplicate triangle removal).
 * <p>
 * Thread-safe: each call owns a fresh {@link PolygonTriangulator} and
 * operates solely on its inputs.
 */
public final class GeometryMeshBuilder {
    private static final double T_JUNCTION_TOLERANCE_METERS = 0.02;

    private GeometryMeshBuilder() {
    }

    /**
     * Triangulate the given geometry properties into a single mesh.
     * Non-surface geometry types (points, lines) are skipped.
     *
     * @param geometryProperties feature geometry properties to triangulate
     * @param featureId          owning feature id, propagated into the mesh
     * @param texCoordMap        per-ring UV coordinates, or {@code null} if untextured
     * @param ringTextureMap     per-ring texture id, or {@code null} if untextured
     */
    public static TriangleMesh build(List<GeometryProperty> geometryProperties,
                                     long featureId,
                                     Map<LinearRing, List<TextureCoordinate>> texCoordMap,
                                     Map<LinearRing, Integer> ringTextureMap) {
        PolygonTriangulator triangulator = new PolygonTriangulator();
        TriangleMesh mesh = new TriangleMesh();

        for (GeometryProperty property : geometryProperties) {
            Geometry<?> geometry = property.getObject();
            if (geometry == null) {
                continue;
            }

            switch (geometry.getGeometryType()) {
                case POLYGON, MULTI_SURFACE, COMPOSITE_SURFACE, SOLID,
                        MULTI_SOLID, COMPOSITE_SOLID, TRIANGULATED_SURFACE -> {
                    TriangleMesh geomMesh = triangulator.triangulate(geometry, featureId,
                            texCoordMap, ringTextureMap);
                    mesh.merge(geomMesh);
                }
                default -> {
                    // Skip non-surface geometry types (points, lines)
                }
            }
        }

        if (!mesh.isEmpty()) {
            double[] center = mesh.computeCenter();
            double scaleX = GeoTransform.metersPerDegreeLon(center[1]);
            double scaleY = GeoTransform.WGS84_METERS_PER_DEGREE_LAT;
            mesh.resolveTJunctions(scaleX, scaleY, T_JUNCTION_TOLERANCE_METERS);
            mesh.removeDuplicateTriangles();
        }

        return mesh;
    }
}
