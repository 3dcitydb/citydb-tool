/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.encoder.tiles3d;

import org.citydb.vis.util.GeoTransform;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parity test between the two implicit-geometry render paths: a prototype
 * vertex placed via the baked pipeline (per-vertex metric→degree offsets at
 * the anchor, then the encoder's degree→cell-ENU CellFrame math) must land
 * where the GPU-instancing path puts it (prototype vertex in glTF axes ×
 * per-instance TRS from {@link InstanceTransformEncoder}).
 * <p>
 * Both paths use the identical spherical degree→meter approximation by
 * design (see {@code InstanceTransformEncoder} javadoc), so the only residual
 * is the baked path's per-vertex degree conversion happening at the anchor
 * latitude instead of the cell latitude — sub-millimeter at intra-cell
 * distances. A frame/axis mistake in either path shows up as meters.
 */
class InstanceTransformEncoderTest {

    private static final double[] CELL_ANCHOR = {9.1700, 48.7800, 80.0};

    /** Rotate a vector by a unit quaternion [x, y, z, w]. */
    private static double[] rotate(float[] q, double vx, double vy, double vz) {
        double x = q[0], y = q[1], z = q[2], w = q[3];
        double tx = 2 * (y * vz - z * vy);
        double ty = 2 * (z * vx - x * vz);
        double tz = 2 * (x * vy - y * vx);
        return new double[]{
                vx + w * tx + (y * tz - z * ty),
                vy + w * ty + (z * tx - x * tz),
                vz + w * tz + (x * ty - y * tx)
        };
    }

    /**
     * The baked path's placement of a prototype vertex, replicated:
     * {@code ImplicitInstanceTransformer.applyTransform} (metric offset →
     * degrees at the anchor) followed by the GLB encoder's net degree →
     * cell-ENU-Y-up conversion (VertexWelder center subtraction + CellFrame
     * scale/offset collapse to a single scaling around the cell center).
     */
    private static double[] bakedGltfPosition(double[] vertex, double[][] m3x3,
                                              double[] anchor, double[] cellAnchor) {
        double mx = m3x3[0][0] * vertex[0] + m3x3[0][1] * vertex[1] + m3x3[0][2] * vertex[2];
        double my = m3x3[1][0] * vertex[0] + m3x3[1][1] * vertex[1] + m3x3[1][2] * vertex[2];
        double mz = m3x3[2][0] * vertex[0] + m3x3[2][1] * vertex[1] + m3x3[2][2] * vertex[2];
        double lon = anchor[0] + mx / GeoTransform.metersPerDegreeLon(anchor[1]);
        double lat = anchor[1] + my / GeoTransform.WGS84_METERS_PER_DEGREE_LAT;
        double z = anchor[2] + mz;

        double east = (lon - cellAnchor[0]) * GeoTransform.metersPerDegreeLon(cellAnchor[1]);
        double north = (lat - cellAnchor[1]) * GeoTransform.WGS84_METERS_PER_DEGREE_LAT;
        double up = z - cellAnchor[2];
        return new double[]{east, up, -north};
    }

    /** The instancing path: glTF-axes prototype vertex × per-instance TRS. */
    private static double[] instancedGltfPosition(double[] vertex,
                                                  InstanceTransformEncoder.InstanceTrs trs) {
        double gx = vertex[0], gy = vertex[2], gz = -vertex[1];
        double sx = gx * trs.scale()[0], sy = gy * trs.scale()[1], sz = gz * trs.scale()[2];
        double[] rotated = rotate(trs.rotation(), sx, sy, sz);
        return new double[]{
                rotated[0] + trs.translation()[0],
                rotated[1] + trs.translation()[1],
                rotated[2] + trs.translation()[2]
        };
    }

    private static void assertParity(double[] anchor, double yawRad, double scale,
                                     double toleranceMeters) {
        double c = Math.cos(yawRad), s = Math.sin(yawRad);
        double[][] m3x3 = {
                {c * scale, -s * scale, 0},
                {s * scale, c * scale, 0},
                {0, 0, scale}
        };
        // Quaternion for a rotation about the ENU up axis by yawRad.
        float[] rotation = {0f, 0f, (float) Math.sin(yawRad / 2), (float) Math.cos(yawRad / 2)};
        float[] scaleVec = {(float) scale, (float) scale, (float) scale};

        InstanceTransformEncoder.InstanceTrs trs = InstanceTransformEncoder.encode(
                anchor, rotation, scaleVec, CELL_ANCHOR);

        double[][] vertices = {
                {0, 0, 0}, {4, 0, 0}, {0, 3, 0}, {0, 0, 6}, {2, 1, 5}, {-3, -2, 1}
        };
        for (double[] vertex : vertices) {
            double[] baked = bakedGltfPosition(vertex, m3x3, anchor, CELL_ANCHOR);
            double[] instanced = instancedGltfPosition(vertex, trs);
            for (int axis = 0; axis < 3; axis++) {
                assertEquals(baked[axis], instanced[axis], toleranceMeters,
                        "axis " + axis + " of vertex (" + vertex[0] + "," + vertex[1]
                                + "," + vertex[2] + ")");
            }
        }
    }

    @Test
    void instanceAtCellAnchorMatchesBakedPath() {
        // No anchor-to-cell offset: both paths reduce to the same local
        // placement, differences are pure floating-point noise.
        assertParity(CELL_ANCHOR, Math.toRadians(30), 2.0, 1e-3);
    }

    @Test
    void offsetInstanceMatchesBakedPath() {
        // ~110 m east, ~100 m south, 15 m above the cell anchor. Only the
        // anchor-vs-cell-latitude residual of the per-vertex degree conversion
        // and float32 rounding separate the two paths here.
        double[] anchor = {9.1715, 48.7791, 95.0};
        assertParity(anchor, Math.toRadians(-75), 0.8, 0.005);
    }

    @Test
    void identityInstanceKeepsPrototypeShape() {
        InstanceTransformEncoder.InstanceTrs trs = InstanceTransformEncoder.encode(
                CELL_ANCHOR, new float[]{0, 0, 0, 1}, new float[]{1, 1, 1}, CELL_ANCHOR);
        assertEquals(0.0, trs.translation()[0], 1e-6);
        assertEquals(0.0, trs.translation()[1], 1e-6);
        assertEquals(0.0, trs.translation()[2], 1e-6);
        assertEquals(1.0, trs.scale()[0], 1e-6);
        // Identity rotation up to quaternion sign.
        assertTrue(Math.abs(trs.rotation()[3]) > 0.999999);
    }
}
