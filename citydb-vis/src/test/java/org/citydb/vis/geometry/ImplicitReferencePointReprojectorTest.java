/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.geometry;

import org.citydb.model.common.Matrix4x4;
import org.citydb.model.geometry.Coordinate;
import org.citydb.vis.util.GeoTransform;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the grid&rarr;ENU alignment of the implicit-geometry transformation
 * matrix. A projected CRS's grid north is not true north, so a matrix taken
 * straight from the database yaws every instance by the meridian convergence
 * angle. These tests fix three properties the downstream pipeline relies on:
 * the recovered angle is exactly the convergence angle even when the Jacobian
 * carries a scale factor, the correction is a pure rotation (so
 * {@link TrsDecomposition} keeps accepting the matrix and the GPU-instancing
 * path stays available), and degenerate probes leave the matrix untouched
 * rather than corrupting it.
 */
class ImplicitReferencePointReprojectorTest {
    private static final double EPS = 1e-12;
    private static final double ANCHOR_LON = 13.4;
    private static final double ANCHOR_LAT = 52.5;

    private static final Coordinate ANCHOR = Coordinate.of(ANCHOR_LON, ANCHOR_LAT, 34.0);

    /**
     * Build the reprojected probe pair that a source CRS whose grid axes are
     * rotated by {@code gammaRad} against ENU and scaled by {@code scale} would
     * produce. Inverse of the Jacobian estimation in the class under test.
     */
    private static Coordinate probe(double eastMeters, double northMeters) {
        return Coordinate.of(
                ANCHOR_LON + eastMeters / GeoTransform.metersPerDegreeLon(ANCHOR_LAT),
                ANCHOR_LAT + northMeters / GeoTransform.WGS84_METERS_PER_DEGREE_LAT,
                34.0);
    }

    private static Coordinate eastProbe(double gammaRad, double scale) {
        double d = ImplicitReferencePointReprojector.PROBE_DISTANCE * scale;
        return probe(d * Math.cos(gammaRad), d * Math.sin(gammaRad));
    }

    private static Coordinate northProbe(double gammaRad, double scale) {
        double d = ImplicitReferencePointReprojector.PROBE_DISTANCE * scale;
        return probe(-d * Math.sin(gammaRad), d * Math.cos(gammaRad));
    }

    private static Matrix4x4 align(Matrix4x4 m, double gammaRad, double scale) {
        return ImplicitReferencePointReprojector.alignToEnu(m, ANCHOR,
                eastProbe(gammaRad, scale), northProbe(gammaRad, scale));
    }

    @Test
    void gridAxesAlreadyOnEnuLeaveTheMatrixUnchanged() {
        Matrix4x4 m = Matrix4x4.ofRowMajor(
                2, 0, 0, 7,
                0, 3, 0, 8,
                0, 0, 4, 9,
                0, 0, 0, 1);

        Matrix4x4 aligned = align(m, 0, 1);

        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                assertEquals(m.get(row, col), aligned.get(row, col), 1e-9,
                        "element (" + row + "," + col + ")");
            }
        }
    }

    @Test
    void gridNorthIsRotatedOntoTrueNorth() {
        // Berlin in EPSG:25832 (central meridian 9°E): grid north sits ~3.5°
        // west of true north. A prototype edge pointing along grid north must
        // come out pointing 3.5° east of the matrix's own +Y.
        double gamma = Math.toRadians(3.5);
        Matrix4x4 aligned = align(Matrix4x4.identity(), gamma, 1);

        // Column 1 = image of the prototype's +Y axis.
        assertEquals(-Math.sin(gamma), aligned.get(0, 1), 1e-9);
        assertEquals(Math.cos(gamma), aligned.get(1, 1), 1e-9);
        // Column 0 = image of +X.
        assertEquals(Math.cos(gamma), aligned.get(0, 0), 1e-9);
        assertEquals(Math.sin(gamma), aligned.get(1, 0), 1e-9);
        // Z is untouched: source-CRS height and ENU up are the same axis.
        assertEquals(1, aligned.get(2, 2), EPS);
        assertEquals(0, aligned.get(2, 0), EPS);
        assertEquals(0, aligned.get(2, 1), EPS);
    }

    @Test
    void rotationComposesWithTheMatrixOwnRotation() {
        double gamma = Math.toRadians(4.7);
        double own = Math.toRadians(30);
        double c = Math.cos(own), s = Math.sin(own);
        Matrix4x4 m = Matrix4x4.ofRowMajor(
                c, -s, 0, 0,
                s, c, 0, 0,
                0, 0, 1, 0,
                0, 0, 0, 1);

        Matrix4x4 aligned = align(m, gamma, 1);

        // Angles add: the instance's own yaw plus the convergence correction.
        double expected = own + gamma;
        assertEquals(Math.cos(expected), aligned.get(0, 0), 1e-9);
        assertEquals(Math.sin(expected), aligned.get(1, 0), 1e-9);
    }

    @Test
    void jacobianScaleIsDiscardedSoOnlyTheRotationSurvives() {
        // A real projection's Jacobian carries the scale factor (UTM: 0.9996 …
        // 1.001) on top of the rotation. Correcting it would add anisotropic
        // scale — deliberately dropped, so column lengths must be preserved.
        double gamma = Math.toRadians(3.5);
        Matrix4x4 m = Matrix4x4.ofRowMajor(
                5, 0, 0, 0,
                0, 5, 0, 0,
                0, 0, 5, 0,
                0, 0, 0, 1);

        Matrix4x4 aligned = align(m, gamma, 0.9996);

        assertEquals(5, Math.hypot(aligned.get(0, 0), aligned.get(1, 0)), 1e-9,
                "column 0 length must be unchanged by the correction");
        assertEquals(5, Math.hypot(aligned.get(0, 1), aligned.get(1, 1)), 1e-9,
                "column 1 length must be unchanged by the correction");
        // The rotation itself is still recovered exactly despite the scale.
        assertEquals(5 * Math.cos(gamma), aligned.get(0, 0), 1e-9);
        assertEquals(5 * Math.sin(gamma), aligned.get(1, 0), 1e-9);
    }

    @Test
    void correctedMatrixStillDecomposesForGpuInstancing() {
        // The correction must not push a decomposable matrix past
        // TrsDecomposition's 1e-4 shear tolerance — that would silently drop
        // instances back to the baked path.
        double gamma = Math.toRadians(4.7);
        double own = Math.toRadians(41);
        double c = Math.cos(own), s = Math.sin(own);
        Matrix4x4 m = Matrix4x4.ofRowMajor(
                2 * c, -3 * s, 0, 0,
                2 * s, 3 * c, 0, 0,
                0, 0, 4, 0,
                0, 0, 0, 1);

        TrsDecomposition.Result before = TrsDecomposition.decompose(m);
        assertNotNull(before, "precondition: the uncorrected matrix decomposes");

        TrsDecomposition.Result after = TrsDecomposition.decompose(align(m, gamma, 0.9996));
        assertNotNull(after, "correction must not introduce shear");
        for (int axis = 0; axis < 3; axis++) {
            assertEquals(before.scale()[axis], after.scale()[axis], 1e-9,
                    "a pure rotation must leave the per-axis scale unchanged");
        }
    }

    @Test
    void degenerateProbesLeaveTheMatrixUntouched() {
        Matrix4x4 m = Matrix4x4.ofRowMajor(
                1, 2, 3, 4,
                5, 6, 7, 8,
                9, 10, 11, 12,
                0, 0, 0, 1);

        Matrix4x4 aligned = ImplicitReferencePointReprojector.alignToEnu(m, ANCHOR, ANCHOR, ANCHOR);

        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                assertEquals(m.get(row, col), aligned.get(row, col), EPS);
            }
        }
    }

    @Test
    void uncorrectedYawWouldDisplaceVerticesByTheConvergenceAngle() {
        // Quantifies what the correction buys: a vertex 10 m out from the
        // anchor sits ~82 cm off at the 4.7° convergence angle reached at
        // Germany's eastern border in EPSG:25832.
        double gamma = Math.toRadians(4.7);
        Matrix4x4 aligned = align(Matrix4x4.identity(), gamma, 1);

        double x = 10, y = 0;
        double correctedX = aligned.get(0, 0) * x + aligned.get(0, 1) * y;
        double correctedY = aligned.get(1, 0) * x + aligned.get(1, 1) * y;
        double displacement = Math.hypot(correctedX - x, correctedY - y);

        assertEquals(2 * 10 * Math.sin(gamma / 2), displacement, 1e-9);
        assertTrue(displacement > 0.8 && displacement < 0.83,
                "expected ~0.82 m, got " + displacement);
    }
}
