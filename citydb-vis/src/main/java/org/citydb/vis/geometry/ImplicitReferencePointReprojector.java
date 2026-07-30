/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.geometry;

import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.adapter.GeometryAdapter;
import org.citydb.database.geometry.GeometryException;
import org.citydb.model.common.Matrix4x4;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.MultiPoint;
import org.citydb.model.geometry.Point;
import org.citydb.model.property.ImplicitGeometryProperty;
import org.citydb.model.util.GeometryInfo;
import org.citydb.vis.util.GeoTransform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;

/**
 * Brings each implicit-geometry instance into the frame
 * {@link ImplicitInstanceTransformer} expects: reference point in EPSG:4326,
 * transformation matrix on local ENU axes with a zero translation column.
 * Both halves of that contract need work, because the database delivers the
 * reference point and the matrix in the source CRS (typically UTM meters) —
 * the schema's hierarchy SQL skips {@code ST_Transform} on
 * {@code val_implicitgeom_refpoint}, and the matrix is stored as a raw float
 * array that no reprojection ever touches.
 *
 * <p>Per implicit-geometry instance:
 * <ol>
 *   <li><b>Anchor.</b> Compute {@code anchor_source = referencePoint + M[*][3]},
 *       reproject it to EPSG:4326 via {@link GeometryAdapter#transform}, write
 *       it back as the new referencePoint, and zero the matrix's translation
 *       column.</li>
 *   <li><b>Orientation.</b> Rotate {@code M_3x3} from source-CRS grid axes onto
 *       ENU axes by the meridian convergence angle measured at the anchor.</li>
 * </ol>
 *
 * <p><b>Why fold the translation into the anchor instead of reprojecting
 * just the referencePoint:</b> source-CRS grid axes (UTM east/north)
 * differ from geographic ENU by the meridian-convergence angle. Reprojecting
 * just the referencePoint would leave {@code M_translation} on grid axes
 * while we treat it as ENU, shifting every instance by a few meters per
 * ~100 m of translation. Reprojecting {@code anchor = ref + M_translation}
 * as one point absorbs the grid rotation exactly.
 *
 * <p><b>Why the 3x3 part needs rotating too:</b> a projected CRS's grid north
 * is not true north. The two differ by the meridian convergence angle
 * {@code γ ≈ Δλ · sin(φ)}, zero only on the projection's central meridian and
 * at the equator. Germany-wide data in EPSG:25832 reaches γ ≈ 4.7° at the
 * eastern border. Left uncorrected, every instance is yawed by γ: a vertex at
 * distance {@code r} from the anchor lands {@code r · γ} off, and — more
 * visibly — oriented objects (signs, shelters, railings) no longer line up
 * with the surrounding explicit geometry, which PostGIS reprojected exactly.
 *
 * <p>γ is measured numerically rather than derived from projection parameters,
 * which keeps this projection-agnostic: two probe points offset from the anchor
 * along the source-CRS axes ride along in the same {@code ST_Transform} call as
 * the anchor itself, so the correction costs no extra round-trip. Their images
 * give the grid→ENU Jacobian, from which the closest pure rotation is taken.
 *
 * <p>Deliberately only the rotation is applied, not the full Jacobian. The
 * Jacobian also carries the projection's scale factor (UTM: 0.9996 … 1.001)
 * and the error of the transformer's constant-degree approximation (~2e-3 in
 * longitude), which together amount to a slight anisotropic scale. Correcting
 * those would buy millimetres on a prototype-sized offset while introducing
 * ~1.4e-3 of shear into {@code M_3x3} — an order of magnitude past
 * {@code TrsDecomposition}'s 1e-4 shear tolerance, which would drop instances
 * out of the GPU-instancing path for no visible gain. A pure rotation leaves
 * the matrix's Gram matrix untouched, so decomposability is preserved exactly.
 *
 * <p>Each instance costs one SQL round-trip; for features with hundreds of
 * instances this means hundreds of round-trips. Acceptable for typical
 * city-scale data; revisit with a per-region Jacobian cache if profiling shows
 * a hot path (γ varies by only ~0.01°/km, so one probe per square kilometre
 * would be plenty).
 */
public final class ImplicitReferencePointReprojector {
    private static final Logger logger = LoggerFactory.getLogger(ImplicitReferencePointReprojector.class);
    private static final int TARGET_SRID = 4326;

    // Finite-difference step for the grid->ENU Jacobian, in source-CRS meters.
    // Large enough that the reprojected offsets carry plenty of significant
    // digits, small enough that curvature over the step is irrelevant.
    static final double PROBE_DISTANCE = 100.0;

    private ImplicitReferencePointReprojector() {
    }

    public static void reproject(Feature feature, DatabaseAdapter adapter) {
        int sourceSRID = adapter.getDatabaseMetadata().getSpatialReference().getSRID();
        if (sourceSRID == TARGET_SRID) {
            return;
        }
        // Collect implicit geometries across the whole feature subtree.
        // Mode must match what VisWriter.write uses (INCLUDE_CONTAINED_FEATURES)
        // — otherwise we'd reproject anchors VisWriter doesn't process, or
        // (worse) leave anchors VisWriter does process unprojected.
        GeometryInfo geometryInfo = feature.getGeometryInfo(
                GeometryInfo.Mode.INCLUDE_CONTAINED_FEATURES);
        if (!geometryInfo.hasImplicitGeometries()) {
            return;
        }
        GeometryAdapter geometryAdapter = adapter.getGeometryAdapter();
        for (ImplicitGeometryProperty property : geometryInfo.getImplicitGeometries()) {
            Point ref = property.getReferencePoint().orElse(null);
            Matrix4x4 transformationMatrix = property.getTransformationMatrix().orElse(null);
            if (ref == null || transformationMatrix == null) {
                continue;
            }
            // Combine refPoint + M_translation into a single source-CRS anchor,
            // then reproject the anchor to 4326. This is what folds out the
            // UTM grid-vs-ENU rotation; see the class javadoc.
            Coordinate refCoord = ref.getCoordinate();
            double anchorX = refCoord.getX() + transformationMatrix.get(0, 3);
            double anchorY = refCoord.getY() + transformationMatrix.get(1, 3);
            double anchorZ = (refCoord.getDimension() == 3 ? refCoord.getZ() : 0.0)
                    + transformationMatrix.get(2, 3);
            // The anchor plus two probes offset along the source-CRS axes, sent
            // as one MultiPoint so the whole instance costs a single round-trip.
            //
            // Force SRID to the database's source SRS regardless of any
            // SRID stamp the JDBC driver attached to the value. The
            // val_implicitgeom_refpoint column is declared with SRID 4326
            // in the schema, so PostGIS labels every retrieved point as
            // 4326 even though the feature-hierarchy SQL skips the transform
            // and the actual coordinate values stay in source CRS. Without
            // this override, GeometryAdapter.transform short-circuits as
            // a no-op (sourceSRID == targetSRID).
            MultiPoint probes = MultiPoint.of(List.of(
                    Point.of(Coordinate.of(anchorX, anchorY, anchorZ)),
                    Point.of(Coordinate.of(anchorX + PROBE_DISTANCE, anchorY, anchorZ)),
                    Point.of(Coordinate.of(anchorX, anchorY + PROBE_DISTANCE, anchorZ)))
            ).setSRID(sourceSRID);
            try {
                List<Point> projected = geometryAdapter.transform(probes, TARGET_SRID).getPoints();
                if (projected.size() != 3) {
                    throw new GeometryException("Expected 3 reprojected probe points, got "
                            + projected.size() + ".");
                }
                Coordinate anchor = projected.get(0).getCoordinate();
                // Detach from the MultiPoint rather than re-parenting its child.
                Point anchor4326 = Point.of(Coordinate.of(anchor.getX(), anchor.getY(),
                        anchor.getDimension() == 3 ? anchor.getZ() : 0.0)).setSRID(TARGET_SRID);

                // Rotate M_3x3 onto ENU axes, then replace the anchor and zero
                // the matrix translation. The downstream transformer now applies
                // an ENU-aligned M_3x3 to prototype vertices and converts the
                // (small) result to degrees at the 4326 anchor latitude.
                Matrix4x4 aligned = alignToEnu(transformationMatrix,
                        anchor, projected.get(1).getCoordinate(), projected.get(2).getCoordinate());
                property.setReferencePoint(anchor4326);
                property.setTransformationMatrix(Matrix4x4.of(aligned.copy()
                        .set(0, 3, 0)
                        .set(1, 3, 0)
                        .set(2, 3, 0)));
            } catch (GeometryException | SQLException e) {
                logger.warn("Failed to reproject implicit reference point on feature {}: {}",
                        feature.getObjectId().orElse("?"), e.getMessage());
            }
        }
    }

    /**
     * Rotate the matrix's 3x3 part from source-CRS grid axes onto ENU axes at
     * the anchor. The probes are the reprojected images of the anchor offset by
     * {@link #PROBE_DISTANCE} along source-CRS +X and +Y; expressing their
     * displacement in the same metric-to-degree scale the transformer uses
     * yields the grid&rarr;ENU Jacobian directly.
     *
     * @return the rotated matrix, or the input unchanged if the probes are
     * degenerate
     */
    static Matrix4x4 alignToEnu(Matrix4x4 matrix,
                                Coordinate anchor,
                                Coordinate eastProbe,
                                Coordinate northProbe) {
        double metersPerDegLon = GeoTransform.metersPerDegreeLon(anchor.getY());
        // Columns of the Jacobian: ENU images of the source-CRS +X and +Y axes.
        double j00 = (eastProbe.getX() - anchor.getX()) * metersPerDegLon / PROBE_DISTANCE;
        double j10 = (eastProbe.getY() - anchor.getY())
                * GeoTransform.WGS84_METERS_PER_DEGREE_LAT / PROBE_DISTANCE;
        double j01 = (northProbe.getX() - anchor.getX()) * metersPerDegLon / PROBE_DISTANCE;
        double j11 = (northProbe.getY() - anchor.getY())
                * GeoTransform.WGS84_METERS_PER_DEGREE_LAT / PROBE_DISTANCE;

        // Closest pure rotation to J (2D orthogonal Procrustes): for J = s·R(γ)
        // this recovers γ exactly, and for the near-conformal J a projection
        // actually produces it is the least-squares fit. Discarding the scale
        // part is deliberate — see the class javadoc.
        double sin = j10 - j01;
        double cos = j00 + j11;
        if (Math.abs(sin) + Math.abs(cos) < 1e-9) {
            logger.warn("Degenerate grid-to-ENU Jacobian at implicit-geometry anchor; " +
                    "leaving the transformation matrix on source-CRS axes.");
            return matrix;
        }
        double gamma = Math.atan2(sin, cos);
        double c = Math.cos(gamma);
        double s = Math.sin(gamma);

        // M' = Rz(gamma) · M. Rotating the rows leaves the Gram matrix, and
        // hence TrsDecomposition's shear and scale verdicts, unchanged.
        Matrix4x4 rotated = matrix.copy();
        for (int column = 0; column < 3; column++) {
            double x = matrix.get(0, column);
            double y = matrix.get(1, column);
            rotated.set(0, column, c * x - s * y);
            rotated.set(1, column, s * x + c * y);
        }
        return rotated;
    }
}
