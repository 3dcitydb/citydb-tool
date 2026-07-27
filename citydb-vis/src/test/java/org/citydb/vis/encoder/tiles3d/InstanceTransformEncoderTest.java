/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.encoder.tiles3d;

import org.citydb.model.common.Matrix4x4;
import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.LinearRing;
import org.citydb.model.geometry.Point;
import org.citydb.model.geometry.Polygon;
import org.citydb.vis.geometry.ImplicitInstanceTransformer;
import org.citydb.vis.scene.BoundingVolume;
import org.junit.jupiter.api.Test;

import java.util.List;

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
     * The baked path's placement of a prototype vertex, driven by the actual
     * production code so this side of the parity cannot silently drift: a
     * one-vertex prototype ring goes through
     * {@link ImplicitInstanceTransformer#transform} (metric offset → degrees
     * at the anchor), then the real {@link CellFrame#from} scale/offset plus
     * the ENU → glTF Y-up emission of {@code GlbPrimitiveBuilder.build}
     * (east, up, −north; float welded positions like the production mesh).
     * Only the {@code VertexWelder} node-center subtraction is inlined —
     * with the mbs centered on the cell anchor the welder+frame algebra is
     * center-independent, and running the real welder would need full mesh
     * scaffolding for a one-line subtraction.
     */
    private static double[] bakedGltfPosition(double[] vertex, double[][] m3x3,
                                              double[] anchor, double[] cellAnchor) {
        Polygon prototype = Polygon.of(LinearRing.of(List.of(
                Coordinate.of(vertex[0], vertex[1], vertex[2]))));
        Matrix4x4 matrix = Matrix4x4.ofRowMajor(
                m3x3[0][0], m3x3[0][1], m3x3[0][2], 0,
                m3x3[1][0], m3x3[1][1], m3x3[1][2], 0,
                m3x3[2][0], m3x3[2][1], m3x3[2][2], 0,
                0, 0, 0, 1);
        Point referencePoint = Point.of(Coordinate.of(anchor[0], anchor[1], anchor[2]));
        Polygon placed = (Polygon) ImplicitInstanceTransformer
                .transform(prototype, matrix, referencePoint)
                .geometry();
        Coordinate position = placed.getExteriorRing().getPoints().get(0);

        BoundingVolume mbs = BoundingVolume.ofBoundingBox(
                cellAnchor[0], cellAnchor[1], cellAnchor[2],
                cellAnchor[0], cellAnchor[1], cellAnchor[2]);
        CellFrame frame = CellFrame.from(mbs, cellAnchor);
        float weldedX = (float) (position.getX() - mbs.getCenterX());
        float weldedY = (float) (position.getY() - mbs.getCenterY());
        float weldedZ = (float) (position.getZ() - mbs.getCenterZ());
        float east = weldedX * (float) frame.scaleX() + frame.offsetX();
        float north = weldedY * (float) frame.scaleY() + frame.offsetY();
        float up = weldedZ * (float) frame.scaleZ() + frame.offsetZ();
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
        double[][] rotationEnu = {
                {c, -s, 0},
                {s, c, 0},
                {0, 0, 1}
        };
        // Quaternion for a rotation about the ENU up axis by yawRad.
        float[] rotation = {0f, 0f, (float) Math.sin(yawRad / 2), (float) Math.cos(yawRad / 2)};
        float[] scaleEnu = {(float) scale, (float) scale, (float) scale};
        assertParity(anchor, rotationEnu, rotation, scaleEnu, toleranceMeters);
    }

    private static void assertParity(double[] anchor, double[][] rotationEnu, float[] rotation,
                                     float[] scaleEnu, double toleranceMeters) {
        // Guard against fixture typos: the encoder assumes a unit quaternion.
        double normSq = rotation[0] * rotation[0] + rotation[1] * rotation[1]
                + rotation[2] * rotation[2] + rotation[3] * rotation[3];
        assertEquals(1.0, normSq, 1e-6, "rotation fixture must be a unit quaternion");

        // The baked path applies M = R·S (TRS order: scale first, then
        // rotate), i.e. column j of the rotation matrix scaled by s_j.
        double[][] m3x3 = new double[3][3];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                m3x3[row][col] = rotationEnu[row][col] * scaleEnu[col];
            }
        }

        InstanceTransformEncoder.InstanceTrs trs = InstanceTransformEncoder.encode(
                anchor, rotation, scaleEnu, CELL_ANCHOR);

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

    @Test
    void nonUniformScaleLandsInSwappedGltfSlots() {
        // ENU scale (s_east, s_north, s_up) = (0.5, 0.75, 1.0). The axis map
        // P sends east→glTF X, up→glTF Y, north→glTF −Z; P·S·Pᵀ's sign flips
        // cancel on the diagonal, so glTF SCALE = (s_east, s_up, s_north)
        // = (0.5, 1.0, 0.75).
        InstanceTransformEncoder.InstanceTrs trs = InstanceTransformEncoder.encode(
                CELL_ANCHOR, new float[]{0, 0, 0, 1}, new float[]{0.5f, 0.75f, 1.0f},
                CELL_ANCHOR);
        assertEquals(0.5f, trs.scale()[0], 1e-6f);
        assertEquals(1.0f, trs.scale()[1], 1e-6f);
        assertEquals(0.75f, trs.scale()[2], 1e-6f);
    }

    @Test
    void nonUniformScaleInstanceMatchesBakedPath() {
        // Identity rotation, three distinct residual scales: places every
        // prototype axis with its own factor, so any change to the encoder's
        // ENU→glTF scale permutation moves vertices by meters.
        double[][] rotationEnu = {
                {1, 0, 0},
                {0, 1, 0},
                {0, 0, 1}
        };
        double[] anchor = {9.1712, 48.7805, 88.0};
        assertParity(anchor, rotationEnu, new float[]{0, 0, 0, 1},
                new float[]{0.5f, 0.75f, 1.0f}, 0.005);
    }

    @Test
    void pitchedInstanceMatchesBakedPath() {
        // 30° rotation about the horizontal ENU axis (1,1,0)/√2 — a combined
        // pitch and roll that leaves no rotation-matrix entry at zero, so all
        // nine entries of the encoder's P·R·Pᵀ conjugation carry weight
        // (yaw-only fixtures null out four of them).
        double angle = Math.toRadians(30);
        double c = Math.cos(angle), s = Math.sin(angle), k = 1 - c;
        double invSqrt2 = 1 / Math.sqrt(2);
        // Rodrigues: R = cos·I + (1−cos)·aaᵀ + sin·[a]× for a = (1,1,0)/√2.
        double[][] rotationEnu = {
                {c + k / 2, k / 2, s * invSqrt2},
                {k / 2, c + k / 2, -s * invSqrt2},
                {-s * invSqrt2, s * invSqrt2, c}
        };
        float[] rotation = {
                (float) (Math.sin(angle / 2) * invSqrt2),
                (float) (Math.sin(angle / 2) * invSqrt2),
                0f,
                (float) Math.cos(angle / 2)
        };
        double[] anchor = {9.1685, 48.7808, 71.0};
        assertParity(anchor, rotationEnu, rotation, new float[]{0.9f, 0.9f, 0.9f}, 0.005);
    }
}
