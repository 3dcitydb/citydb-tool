/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.geometry;

import org.citydb.model.common.Matrix4x4;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pins the R·S decomposition contract the GPU-instancing path depends on:
 * a matrix that decomposes must reproduce itself exactly from quaternion +
 * scale (that's what guarantees instanced placement matches the baked
 * per-vertex transform), and shear / mirroring / degenerate matrices must be
 * rejected so those instances fall back to baking instead of rendering
 * silently wrong.
 */
class TrsDecompositionTest {

    /** Rebuild the 3×3 from the decomposition and compare against the input. */
    private static void assertRoundTrip(Matrix4x4 m, TrsDecomposition.Result result,
                                        double eps) {
        double[] q = result.rotation();
        double[] s = result.scale();
        double x = q[0], y = q[1], z = q[2], w = q[3];
        double[][] r = {
                {1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)},
                {2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)},
                {2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)}
        };
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                assertEquals(m.get(row, col), r[row][col] * s[col], eps,
                        "element (" + row + "," + col + ")");
            }
        }
    }

    private static Matrix4x4 rotationZ(double angleRad, double sx, double sy, double sz) {
        double c = Math.cos(angleRad), s = Math.sin(angleRad);
        return Matrix4x4.ofRowMajor(
                c * sx, -s * sy, 0, 0,
                s * sx, c * sy, 0, 0,
                0, 0, sz, 0,
                0, 0, 0, 1);
    }

    @Test
    void identityDecomposesToIdentity() {
        TrsDecomposition.Result result = TrsDecomposition.decompose(Matrix4x4.identity());
        assertNotNull(result);
        assertEquals(1.0, result.scale()[0], 1e-12);
        assertEquals(1.0, result.scale()[1], 1e-12);
        assertEquals(1.0, result.scale()[2], 1e-12);
        // Identity quaternion (up to sign).
        assertEquals(1.0, Math.abs(result.rotation()[3]), 1e-12);
        assertRoundTrip(Matrix4x4.identity(), result, 1e-12);
    }

    @Test
    void rotationWithUniformScaleRoundTrips() {
        Matrix4x4 m = rotationZ(Math.toRadians(37.5), 2.5, 2.5, 2.5);
        TrsDecomposition.Result result = TrsDecomposition.decompose(m);
        assertNotNull(result);
        assertEquals(2.5, result.scale()[0], 1e-12);
        assertEquals(2.5, result.scale()[1], 1e-12);
        assertEquals(2.5, result.scale()[2], 1e-12);
        assertRoundTrip(m, result, 1e-12);
    }

    @Test
    void nonUniformScaleWithRotationRoundTrips() {
        // R·S with distinct per-axis scales is exactly representable —
        // the Gram matrix stays diagonal because S multiplies columns.
        Matrix4x4 m = rotationZ(Math.toRadians(-12.0), 1.5, 0.75, 3.0);
        TrsDecomposition.Result result = TrsDecomposition.decompose(m);
        assertNotNull(result);
        assertEquals(1.5, result.scale()[0], 1e-12);
        assertEquals(0.75, result.scale()[1], 1e-12);
        assertEquals(3.0, result.scale()[2], 1e-12);
        assertRoundTrip(m, result, 1e-12);
    }

    @Test
    void generalRotationRoundTrips() {
        // Compose rotations about all three axes (a generic orientation);
        // exercises every quaternion branch input pattern.
        double a = Math.toRadians(133), b = Math.toRadians(-71), c = Math.toRadians(23);
        double ca = Math.cos(a), sa = Math.sin(a);
        double cb = Math.cos(b), sb = Math.sin(b);
        double cc = Math.cos(c), sc = Math.sin(c);
        // Rz(a)·Ry(b)·Rx(c)
        Matrix4x4 m = Matrix4x4.ofRowMajor(
                ca * cb, ca * sb * sc - sa * cc, ca * sb * cc + sa * sc, 0,
                sa * cb, sa * sb * sc + ca * cc, sa * sb * cc - ca * sc, 0,
                -sb, cb * sc, cb * cc, 0,
                0, 0, 0, 1);
        TrsDecomposition.Result result = TrsDecomposition.decompose(m);
        assertNotNull(result);
        assertRoundTrip(m, result, 1e-12);
    }

    @Test
    void shearIsRejected() {
        Matrix4x4 shear = Matrix4x4.ofRowMajor(
                1, 0.3, 0, 0,
                0, 1, 0, 0,
                0, 0, 1, 0,
                0, 0, 0, 1);
        assertNull(TrsDecomposition.decompose(shear));
    }

    @Test
    void mirroringIsRejected() {
        Matrix4x4 mirror = Matrix4x4.ofRowMajor(
                -1, 0, 0, 0,
                0, 1, 0, 0,
                0, 0, 1, 0,
                0, 0, 0, 1);
        assertNull(TrsDecomposition.decompose(mirror));
    }

    @Test
    void degenerateAxisIsRejected() {
        Matrix4x4 flat = Matrix4x4.ofRowMajor(
                1, 0, 0, 0,
                0, 1, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 1);
        assertNull(TrsDecomposition.decompose(flat));
    }

    @Test
    void translationColumnIsIgnored() {
        // The translation column is folded into the anchor by the reprojector /
        // instance processor; the decomposition must not be affected by it.
        double c = Math.cos(Math.toRadians(90)), s = Math.sin(Math.toRadians(90));
        Matrix4x4 m = Matrix4x4.ofRowMajor(
                c, -s, 0, 123.0,
                s, c, 0, -45.0,
                0, 0, 1, 7.0,
                0, 0, 0, 1);
        TrsDecomposition.Result result = TrsDecomposition.decompose(m);
        assertNotNull(result);
        assertRoundTrip(m, result, 1e-12);
    }
}
