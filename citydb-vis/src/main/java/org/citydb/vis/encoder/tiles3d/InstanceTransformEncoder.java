/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.encoder.tiles3d;

import org.citydb.vis.geometry.TrsDecomposition;
import org.citydb.vis.util.GeoTransform;

/**
 * Converts an instance's placement payload — anchor (EPSG:4326), rotation
 * quaternion (prototype-local → ENU at the anchor), per-axis scale — into the
 * TRS attribute rows of glTF {@code EXT_mesh_gpu_instancing} in the node's
 * cell-local Y-up frame.
 * <p>
 * With {@code R} the payload rotation, {@code S} the payload scale, and
 * {@code P} the ENU→glTF axis map (x=east, y=up, z=−north; a proper
 * rotation, det +1):
 * <pre>
 *   translation = P · [(lon−lon_A)·mPerDegLon(lat_A), (lat−lat_A)·mPerDegLat, z−z_A]
 *   rotation    = quat( P · R · Pᵀ )
 *   scale       = (sx, sz, sy)                       // P·S·Pᵀ, signs cancel
 * </pre>
 * <b>Deliberately the same spherical degree→meter approximation as the baked
 * path</b> ({@code metersPerDegreeLon} / {@code WGS84_METERS_PER_DEGREE_LAT},
 * the exact math {@code VertexWelder} + {@code CellFrame} apply to explicit
 * geometry), NOT exact ECEF differencing. The approximation deviates from the
 * true ellipsoidal tangent frame by ~0.2%·distance at mid latitudes (N/a
 * ratio), but every baked vertex in the tile carries that same deviation —
 * matching it keeps instanced trees seamlessly aligned with their baked
 * neighbours, and toggling {@code --implicit-geometry-instancing} does not
 * move instance <em>placement</em> (the two paths can still differ in weld
 * granularity and shading normals; see {@code GlbEncoder.buildBatchGeometry}
 * and the textured up-normal note in
 * {@code GlbEncoder.buildPrimitiveArrays}). For the same
 * reason no anchor-vs-cell ENU alignment rotation is applied: the baked path
 * treats the whole cell as one flat tangent plane.
 * <p>
 * The prototype mesh itself is encoded with the same {@code P} axis swap
 * (X=east, Y=up, Z=−north at identity scale), which is what makes the
 * {@code P·R·Pᵀ} conjugation the exact per-instance complement.
 */
final class InstanceTransformEncoder {

    /** One instance's TRS row set, ready for the instancing accessors. */
    record InstanceTrs(float[] translation, float[] rotation, float[] scale) {
    }

    private InstanceTransformEncoder() {
    }

    static InstanceTrs encode(double[] anchor, float[] rotationEnu, float[] scaleEnu,
                              double[] cellAnchor) {
        // Cell-ENU offset of the instance anchor, using the identical
        // degree→meter scaling the baked path applies (see class javadoc).
        double te = (anchor[0] - cellAnchor[0]) * GeoTransform.metersPerDegreeLon(cellAnchor[1]);
        double tn = (anchor[1] - cellAnchor[1]) * GeoTransform.WGS84_METERS_PER_DEGREE_LAT;
        double tu = anchor[2] - cellAnchor[2];

        // Conjugate the ENU rotation by the ENU→glTF axis map
        // P = [[1,0,0],[0,0,1],[0,-1,0]]. P·M·Pᵀ permutes rows (0,2,1) and
        // columns (0,2,1) with sign flips on the moved row/column: written
        // out directly instead of two generic multiplies.
        double[][] r = quaternionToMatrix(rotationEnu);
        double[][] g = new double[][]{
                {r[0][0], r[0][2], -r[0][1]},
                {r[2][0], r[2][2], -r[2][1]},
                {-r[1][0], -r[1][2], r[1][1]}
        };
        double[] q = TrsDecomposition.toQuaternion(
                g[0][0], g[0][1], g[0][2],
                g[1][0], g[1][1], g[1][2],
                g[2][0], g[2][1], g[2][2]);

        return new InstanceTrs(
                new float[]{(float) te, (float) tu, (float) -tn},
                new float[]{(float) q[0], (float) q[1], (float) q[2], (float) q[3]},
                new float[]{scaleEnu[0], scaleEnu[2], scaleEnu[1]});
    }

    /** Unit quaternion [x, y, z, w] → 3×3 rotation matrix. */
    private static double[][] quaternionToMatrix(float[] q) {
        double x = q[0], y = q[1], z = q[2], w = q[3];
        return new double[][]{
                {1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)},
                {2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)},
                {2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)}
        };
    }
}
