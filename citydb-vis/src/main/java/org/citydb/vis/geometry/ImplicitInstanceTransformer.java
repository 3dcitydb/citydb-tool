/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.geometry;

import org.citydb.model.common.Matrix4x4;
import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.Geometry;
import org.citydb.model.geometry.LinearRing;
import org.citydb.model.geometry.Point;
import org.citydb.model.geometry.Polygon;
import org.citydb.model.walker.ModelWalker;
import org.citydb.vis.util.GeoTransform;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Materializes a CityGML implicit-geometry instance into world-space (EPSG:4326)
 * by deep-copying the prototype geometry, applying the per-instance 4×4
 * transformation matrix and reference point, and exposing the prototype-to-
 * instance ring identity bridge so callers can remap any per-ring side data
 * (e.g. appearance maps) onto the copy's ring identities.
 *
 * <p><b>Coordinate model:</b>
 * <ul>
 *   <li>Prototype geometry: local Cartesian meters.</li>
 *   <li>Transformation matrix M: rotation/scale + translation in source-CRS
 *       meters (CityGML's transformationMatrix is unit-preserving).</li>
 *   <li>Reference point: target SRS, EPSG:4326 (longitude/latitude in degrees,
 *       Z in meters). The DB exporter forces target SRS = 4326 for vis-export.</li>
 * </ul>
 *
 * <p><b>Per-vertex placement:</b>
 * <pre>
 *   m  = M_3x3 · v + M[*][3]                           // metric offset
 *   pos.lon = refPoint.lon + m.x / metersPerDegreeLon(refPoint.lat)
 *   pos.lat = refPoint.lat + m.y / WGS84_METERS_PER_DEGREE_LAT
 *   pos.z   = refPoint.z   + m.z
 * </pre>
 * The metric→degree conversion uses a spherical WGS84 approximation that is
 * adequate for the local extent of a single implicit instance.
 *
 * <p><b>Why pre-transform instead of mesh-side transform:</b> placing the
 * prototype into 4326 before triangulation lets every downstream stage
 * (PolygonTriangulator, T-junction resolution, appearance consumers, styling)
 * reuse the existing explicit-geometry pipeline unchanged. The trade-off is
 * one prototype copy + N coordinate transforms per instance.
 *
 * <p><b>Appearance remap</b> is the caller's job: the result exposes the
 * {@code Map<LinearRing, LinearRing>} bridge built during the deep-copy walk;
 * callers with prototype-ring-keyed data (e.g. {@code RingAppearance} from
 * {@code AppearanceExtractor}) translate it through that bridge themselves.
 * Keeping the transformer free of any appearance type lets the
 * {@code geometry} package stay below {@code appearance} in the layering.
 */
public final class ImplicitInstanceTransformer {

    public record Result(Geometry<?> geometry,
                         Map<LinearRing, LinearRing> ringMap) {
    }

    private ImplicitInstanceTransformer() {
    }

    /**
     * Build a per-instance copy of the prototype geometry placed at the given
     * reference point with the given transformation matrix, plus the
     * prototype-ring → instance-ring identity bridge.
     */
    public static Result transform(Geometry<?> prototype,
                                   Matrix4x4 transformationMatrix,
                                   Point referencePoint) {
        Geometry<?> copy = deepCopy(prototype);

        // Build prototype-ring → copy-ring identity map by parallel walks.
        // Every Geometry.copy() implementation preserves child order, so the
        // ModelWalker traversal yields the same ring sequence on both sides.
        List<LinearRing> oldRings = collectRings(prototype);
        List<LinearRing> newRings = collectRings(copy);
        if (oldRings.size() != newRings.size()) {
            throw new IllegalStateException(
                    "Implicit-geometry copy produced a different ring count than the prototype.");
        }
        Map<LinearRing, LinearRing> ringMap = new IdentityHashMap<>(oldRings.size());
        for (int i = 0; i < oldRings.size(); i++) {
            ringMap.put(oldRings.get(i), newRings.get(i));
        }

        applyTransform(newRings, transformationMatrix, referencePoint);

        return new Result(copy, ringMap);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Geometry<?> deepCopy(Geometry<?> geometry) {
        return (Geometry<?>) ((Geometry) geometry).copy();
    }

    private static List<LinearRing> collectRings(Geometry<?> geometry) {
        List<LinearRing> rings = new ArrayList<>();
        geometry.accept(new ModelWalker() {
            @Override
            public void visit(Polygon polygon) {
                rings.add(polygon.getExteriorRing());
                if (polygon.hasInteriorRings()) {
                    rings.addAll(polygon.getInteriorRings());
                }
            }
        });
        return rings;
    }

    private static void applyTransform(List<LinearRing> rings,
                                       Matrix4x4 m,
                                       Point referencePoint) {
        double m00 = m.get(0, 0), m01 = m.get(0, 1), m02 = m.get(0, 2), m03 = m.get(0, 3);
        double m10 = m.get(1, 0), m11 = m.get(1, 1), m12 = m.get(1, 2), m13 = m.get(1, 3);
        double m20 = m.get(2, 0), m21 = m.get(2, 1), m22 = m.get(2, 2), m23 = m.get(2, 3);

        Coordinate ref = referencePoint.getCoordinate();
        double refLon = ref.getX();
        double refLat = ref.getY();
        double refZ = ref.getDimension() == 3 ? ref.getZ() : 0.0;
        double metersPerDegLon = GeoTransform.metersPerDegreeLon(refLat);

        for (LinearRing ring : rings) {
            for (Coordinate c : ring.getPoints()) {
                double x = c.getX();
                double y = c.getY();
                double z = c.getDimension() == 3 ? c.getZ() : 0.0;
                double mx = m00 * x + m01 * y + m02 * z + m03;
                double my = m10 * x + m11 * y + m12 * z + m13;
                double mz = m20 * x + m21 * y + m22 * z + m23;
                c.setX(refLon + mx / metersPerDegLon)
                        .setY(refLat + my / GeoTransform.WGS84_METERS_PER_DEGREE_LAT)
                        .setZ(refZ + mz);
            }
        }
    }
}
