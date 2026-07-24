/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.geometry;

import org.citydb.model.common.Name;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.Geometry;
import org.citydb.model.property.GeometryProperty;
import org.citydb.vis.util.GeoTransform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Converts a feature's geometry properties into a single {@link TriangleMesh}
 * via polygon triangulation and geometry-level post-processing
 * (T-junction crack resolution, duplicate triangle removal).
 * <p>
 * Thread-safe: each call owns a fresh {@link PolygonTriangulator} and
 * operates solely on its inputs.
 */
public final class GeometryMeshBuilder {
    private static final Logger logger = LoggerFactory.getLogger(GeometryMeshBuilder.class);

    /**
     * T-junction snap tolerance in world meters. Public so the prototype
     * registry can rerun the deferred T-junction pass on implicit-geometry
     * templates with this tolerance divided by the largest instance scale
     * (template-local units are arbitrary — the instance matrix defines
     * their meter size, which is unknown at template registration time).
     */
    public static final double T_JUNCTION_TOLERANCE_METERS = 0.02;

    private GeometryMeshBuilder() {
    }

    /**
     * Triangulate the given geometry properties into a single mesh.
     * Non-surface geometry types (points, lines) are skipped.
     * <p>
     * Each property's owning Feature ancestor (recovered via
     * {@link GeometryProperty#getParent(Class)}) becomes the per-triangle
     * source surface type for that property's geometry. This is what gives
     * a CityGML 3.0 Building's RoofSurface, WallSurface and GroundSurface
     * triangles distinct types so the GLB encoder can apply per-feature-type
     * styling within a single building. When a property has no Feature
     * ancestor (e.g. a Building's own LoD1 box that lives directly under
     * the top-level Feature), {@code defaultSurfaceType} is used.
     *
     * @param geometryProperties feature geometry properties to triangulate
     * @param featureId          owning feature id, propagated into the mesh
     * @param defaultSurfaceType fallback surface type for properties whose
     *                           parent walk does not reach a Feature
     * @param ringAttributes     per-ring UV / texture id / X3D-material colour,
     *                           each map {@code null} when absent
     */
    public static TriangleMesh build(List<GeometryProperty> geometryProperties,
                                     long featureId,
                                     Name defaultSurfaceType,
                                     RingAttributes ringAttributes) {
        return build(geometryProperties, featureId, defaultSurfaceType, ringAttributes,
                "feature " + featureId, false);
    }

    /**
     * Build variant with an explicit mesh label and unit model.
     * <p>
     * {@code meshLabel} identifies the mesh's source in warnings emitted
     * during post-processing (currently the T-junction skip warning above
     * the triangle-count ceiling). The {@code featureId} parameter is the
     * export-internal sequential id used for the per-triangle featureId
     * lane — it is <em>not</em> the database feature id, so callers that
     * know a database-traceable identity (gml:id, database id) should put
     * it in the label instead.
     * <p>
     * {@code localMeters} marks geometry in local Cartesian template units
     * (implicit-geometry prototype templates) rather than EPSG:4326,
     * switching the triangulator to identity degree-to-meter scaling. The
     * T-junction pass is skipped in this mode: template units are arbitrary
     * — only the per-instance transformation matrix defines their meter
     * size, so a metric tolerance cannot be applied at build time. The
     * prototype registry reruns the pass at its close-phase boundary with
     * the tolerance divided by the largest observed instance scale.
     */
    public static TriangleMesh build(List<GeometryProperty> geometryProperties,
                                     long featureId,
                                     Name defaultSurfaceType,
                                     RingAttributes ringAttributes,
                                     String meshLabel,
                                     boolean localMeters) {
        PolygonTriangulator triangulator = new PolygonTriangulator(localMeters);
        TriangleMesh mesh = new TriangleMesh();

        // Process properties most-specific-owner-first so that the
        // triangulator's first-wins polygon dedup naturally favors the
        // deepest BoundarySurface owner over a top-level Feature when an
        // xlink references the same polygon from both. Without this sort,
        // a Building's lod3Solid (owner depth = 1) would typically come
        // before its boundedBy RoofSurface (owner depth = 2) in the
        // GeometryInfo, causing the roof polygons to be recorded with
        // type Building and rendered with the building style instead of
        // the configured RoofSurface style. List.sort is stable, so
        // properties at the same depth retain their original order.
        List<GeometryProperty> sortedProperties = new ArrayList<>(geometryProperties);
        sortedProperties.sort(Comparator.comparingInt(GeometryMeshBuilder::featureDepth).reversed());

        for (GeometryProperty property : sortedProperties) {
            Geometry<?> geometry = property.getObject();
            if (geometry == null) {
                continue;
            }

            switch (geometry.getGeometryType()) {
                case POLYGON, MULTI_SURFACE, COMPOSITE_SURFACE, SOLID,
                        MULTI_SOLID, COMPOSITE_SOLID, TRIANGULATED_SURFACE -> {
                    Feature owner = property.getParent(Feature.class);
                    Name surfaceType = owner != null ? owner.getFeatureType() : defaultSurfaceType;
                    TriangleMesh geomMesh = triangulator.triangulate(geometry, featureId,
                            surfaceType, ringAttributes);
                    mesh.merge(geomMesh);
                }
                default -> {
                    // Skip non-surface geometry types (points, lines)
                }
            }
        }

        if (!mesh.isEmpty()) {
            // Single global T-junction pass with a triangle-count ceiling:
            // catches cracks between adjacent polygons within and across
            // GeometryProperty boundaries for normal-sized features, and
            // skips on BIM-scale meshes where the dense overlapping geometry
            // would either explode the algorithm's split-application loop or
            // weld topologically independent components into shared edges
            // (the wrong thing to do). Deferred for prototype templates
            // (localMeters) — see the javadoc above.
            //
            // The ceiling check runs here rather than relying on the
            // resolver's internal backstop so the log level can depend on
            // the source-polygon shape: a mesh whose source polygons are all
            // hole-free triangles is pre-triangulated data (BIM-converted
            // buildings, TINs stored as many small polygons) — its
            // triangulation introduced no new shared-edge cracks beyond
            // what the source mesh already carried, exceeding the ceiling
            // is the expected steady state for such data, and the skip is
            // logged at DEBUG instead of WARN.
            if (!localMeters) {
                if (mesh.getTriangleCount() > TJunctionResolver.MAX_TRIANGLES) {
                    if (triangulator.isSourcePreTriangulated()) {
                        logger.debug("Skipping T-junction resolution for pre-triangulated mesh "
                                + "of {} (triangles={}, all {} source polygons are triangles).",
                                meshLabel, mesh.getTriangleCount(),
                                triangulator.getSourcePolygonCount());
                    } else {
                        logger.warn("Skipping T-junction resolution for oversized mesh of {} "
                                + "(triangles={} > {}; {} of {} source polygons are triangles). "
                                + "Sub-pixel cracks at shared edges (if any) will not be resolved.",
                                meshLabel, mesh.getTriangleCount(), TJunctionResolver.MAX_TRIANGLES,
                                triangulator.getTriangleSourcePolygonCount(),
                                triangulator.getSourcePolygonCount());
                    }
                } else {
                    double[] center = mesh.computeCenter();
                    double scaleX = GeoTransform.metersPerDegreeLon(center[1]);
                    double scaleY = GeoTransform.WGS84_METERS_PER_DEGREE_LAT;
                    mesh.resolveTJunctions(scaleX, scaleY, T_JUNCTION_TOLERANCE_METERS, meshLabel);
                }
            }
            mesh.removeDuplicateTriangles();
        }

        return mesh;
    }

    /**
     * Number of {@link Feature} ancestors of a {@link GeometryProperty} —
     * used to rank properties by owner specificity. A property attached
     * directly to the top-level Feature has depth 1; a property nested
     * inside a {@code boundedBy} BoundarySurface has depth 2; a property
     * deeper still (e.g. a sub-feature of a sub-feature) has depth 3+.
     * Returns 0 only when the property has no Feature ancestor at all,
     * which should not occur in normal flow.
     */
    private static int featureDepth(GeometryProperty property) {
        int depth = 0;
        Feature owner = property.getParent(Feature.class);
        while (owner != null) {
            depth++;
            owner = owner.getParent(Feature.class);
        }
        return depth;
    }
}
