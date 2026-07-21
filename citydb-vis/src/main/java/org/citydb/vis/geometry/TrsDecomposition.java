/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.geometry;

import org.citydb.model.common.Matrix4x4;
import org.citydb.vis.util.GeoTransform;

/**
 * Decomposes the 3×3 part of a CityGML implicit-geometry transformation
 * matrix into a rotation quaternion plus per-axis scale, the only form glTF
 * {@code EXT_mesh_gpu_instancing} can express (T·R·S). The translation
 * column is handled separately by the caller (it is folded into the
 * instance anchor by {@code ImplicitReferencePointReprojector}).
 * <p>
 * For A = R·S the Gram matrix AᵀA equals S², so non-negligible off-diagonal
 * Gram terms mean shear — not representable as R·S, and the instance must
 * fall back to the baked (explicit) pipeline. Mirroring (det&lt;0) is also
 * rejected: a negative glTF scale would flip triangle winding on the shared
 * prototype mesh. Real-world CityGML matrices are almost always rotation
 * plus uniform scale, so rejections are expected to be rare.
 */
public final class TrsDecomposition {

    // The DB stores the matrix as raw doubles but values commonly originate
    // from float-precision sources; 1e-4 relative tolerance accepts that
    // noise while still catching genuine shear.
    private static final double SHEAR_TOLERANCE = 1e-4;
    private static final double MIN_SCALE = 1e-9;

    /**
     * @param rotation unit quaternion {@code [x, y, z, w]} in the matrix's own
     *                 axis convention (prototype-local → ENU-at-anchor)
     * @param scale    per-axis scale {@code [sx, sy, sz]}, all positive
     */
    public record Result(double[] rotation, double[] scale) {
    }

    private TrsDecomposition() {
    }

    /**
     * Decompose the 3×3 part of the given matrix into rotation and scale.
     *
     * @return the decomposition, or {@code null} when the matrix contains
     * shear, mirroring, or a degenerate axis and cannot be expressed as R·S
     */
    public static Result decompose(Matrix4x4 m) {
        double a00 = m.get(0, 0), a01 = m.get(0, 1), a02 = m.get(0, 2);
        double a10 = m.get(1, 0), a11 = m.get(1, 1), a12 = m.get(1, 2);
        double a20 = m.get(2, 0), a21 = m.get(2, 1), a22 = m.get(2, 2);

        // Gram matrix G = AᵀA. Diagonal = squared column lengths = S²;
        // off-diagonal = column dot products = shear indicators.
        double g00 = a00 * a00 + a10 * a10 + a20 * a20;
        double g11 = a01 * a01 + a11 * a11 + a21 * a21;
        double g22 = a02 * a02 + a12 * a12 + a22 * a22;
        double g01 = a00 * a01 + a10 * a11 + a20 * a21;
        double g02 = a00 * a02 + a10 * a12 + a20 * a22;
        double g12 = a01 * a02 + a11 * a12 + a21 * a22;

        double sx = Math.sqrt(g00);
        double sy = Math.sqrt(g11);
        double sz = Math.sqrt(g22);
        if (sx < MIN_SCALE || sy < MIN_SCALE || sz < MIN_SCALE) {
            return null;
        }
        if (Math.abs(g01) > SHEAR_TOLERANCE * sx * sy
                || Math.abs(g02) > SHEAR_TOLERANCE * sx * sz
                || Math.abs(g12) > SHEAR_TOLERANCE * sy * sz) {
            return null;
        }

        // R = A·S^-1 (divide each column by its length).
        double r00 = a00 / sx, r01 = a01 / sy, r02 = a02 / sz;
        double r10 = a10 / sx, r11 = a11 / sy, r12 = a12 / sz;
        double r20 = a20 / sx, r21 = a21 / sy, r22 = a22 / sz;

        double det = r00 * (r11 * r22 - r12 * r21)
                - r01 * (r10 * r22 - r12 * r20)
                + r02 * (r10 * r21 - r11 * r20);
        if (det < 0) {
            return null;
        }

        return new Result(GeoTransform.matrixToQuaternion(
                r00, r01, r02, r10, r11, r12, r20, r21, r22),
                new double[]{sx, sy, sz});
    }
}
