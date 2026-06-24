/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.util;

import org.citydb.vis.util.GeoTransform.EnuBasis;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for the ENU&rarr;ECEF geodetic transforms that every
 * per-node OBB / quaternion in both writers depends on. These pin the
 * load-bearing invariants (per project notes, OBB-always-on with an
 * ENU&rarr;ECEF quaternion is the contract that keeps export compatible with
 * Cesium <em>and</em> ArcGIS Pro / Maps SDK). A sign error or a wrong
 * Shepperd branch here silently misaligns every bounding box without throwing,
 * so the goal is to lock the numeric behaviour against refactors of
 * {@link GeoTransform} rather than to re-derive the math.
 */
class GeoTransformTest {

    private static final double EPS = 1e-9;

    /**
     * Apply a unit quaternion (x, y, z, w) to a vector — the same rotation a
     * client applies to orient an ENU-aligned OBB in ECEF. Used to assert the
     * quaternion rotates the ENU basis vectors onto the exact ECEF columns of
     * {@link EnuBasis#enuToEcefQuaternion}'s source matrix.
     */
    private static double[] rotate(double[] q, double vx, double vy, double vz) {
        double x = q[0], y = q[1], z = q[2], w = q[3];
        // t = 2 * cross(q.xyz, v); v' = v + w*t + cross(q.xyz, t)
        double tx = 2 * (y * vz - z * vy);
        double ty = 2 * (z * vx - x * vz);
        double tz = 2 * (x * vy - y * vx);
        return new double[]{
                vx + w * tx + (y * tz - z * ty),
                vy + w * ty + (z * tx - x * tz),
                vz + w * tz + (x * ty - y * tx)
        };
    }

    private static void assertVecEquals(double[] expected, double[] actual) {
        assertEquals(expected[0], actual[0], 1e-12);
        assertEquals(expected[1], actual[1], 1e-12);
        assertEquals(expected[2], actual[2], 1e-12);
    }

    @Test
    void metersPerDegreeLonShrinksWithLatitude() {
        assertEquals(GeoTransform.WGS84_METERS_PER_DEGREE_LAT,
                GeoTransform.metersPerDegreeLon(0), 1e-6);
        // cos(60) = 0.5, so a degree of longitude is half as long at 60N.
        assertEquals(GeoTransform.WGS84_METERS_PER_DEGREE_LAT * 0.5,
                GeoTransform.metersPerDegreeLon(60), 1e-6);
        // Converges to zero at the pole.
        assertEquals(0.0, GeoTransform.metersPerDegreeLon(90), 1e-6);
    }

    @Test
    void upInEcefMatchesQuaternionUpColumnAcrossLatitudes() {
        // The quaternion must rotate ENU "up" (0,0,1) onto the same ECEF
        // local-vertical that fillUpInEcef writes directly. Checked at the
        // equator, mid latitude, and a high latitude to cover the different
        // Shepperd branches the matrix-to-quaternion conversion selects.
        double[][] points = {
                {0, 0}, {11.5, 48.1}, {-74.0, 40.7}, {10, 89}, {0, -75}
        };
        for (double[] p : points) {
            EnuBasis basis = EnuBasis.at(p[0], p[1]);

            float[] up = new float[3];
            basis.fillUpInEcef(up);

            double[] q = basis.enuToEcefQuaternion();
            double norm = Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
            assertEquals(1.0, norm, 1e-9, "quaternion must be unit length");

            double[] rotatedUp = rotate(q, 0, 0, 1);
            assertEquals(up[0], rotatedUp[0], 1e-6, "up.x at " + p[0] + "," + p[1]);
            assertEquals(up[1], rotatedUp[1], 1e-6, "up.y at " + p[0] + "," + p[1]);
            assertEquals(up[2], rotatedUp[2], 1e-6, "up.z at " + p[0] + "," + p[1]);
        }
    }

    @Test
    void quaternionRotatesEnuBasisOntoEcefMatrixColumns() {
        // The quaternion is the matrix-to-quaternion form of M = [east|north|up].
        // Rotating the ENU unit axes must reproduce those columns exactly.
        EnuBasis basis = EnuBasis.at(11.5, 48.1);
        double sinLon = Math.sin(Math.toRadians(11.5)), cosLon = Math.cos(Math.toRadians(11.5));
        double sinLat = Math.sin(Math.toRadians(48.1)), cosLat = Math.cos(Math.toRadians(48.1));

        double[] q = basis.enuToEcefQuaternion();

        assertVecEquals(new double[]{-sinLon, cosLon, 0}, rotate(q, 1, 0, 0));           // east
        assertVecEquals(new double[]{-sinLat * cosLon, -sinLat * sinLon, cosLat}, rotate(q, 0, 1, 0)); // north
        assertVecEquals(new double[]{cosLat * cosLon, cosLat * sinLon, sinLat}, rotate(q, 0, 0, 1));    // up
    }

    @Test
    void rotateNormalToEcefMatchesUpColumnForUpInput() {
        // rotateNormalToEcef with (0,0,1) is the general form of fillUpInEcef;
        // the two must agree, since the encoders use both interchangeably.
        EnuBasis basis = EnuBasis.at(-74.0, 40.7);
        float[] up = new float[3];
        basis.fillUpInEcef(up);

        float[] rotated = new float[3];
        basis.rotateNormalToEcef(new float[]{0, 0, 1}, rotated);

        assertEquals(up[0], rotated[0], 1e-6);
        assertEquals(up[1], rotated[1], 1e-6);
        assertEquals(up[2], rotated[2], 1e-6);
    }

    @Test
    void enuToEcefMatrixHasOrthonormalRotationAndEllipsoidTranslation() {
        // Column-major 4x4: rotation in the upper-left 3x3 (rows 0..2), the
        // ECEF position of the reference point in the last row.
        double lon = 11.5, lat = 48.1, alt = 100;
        double[] m = GeoTransform.enuToEcefMatrix(new double[]{lon, lat, alt});

        double[] east = {m[0], m[1], m[2]};
        double[] north = {m[4], m[5], m[6]};
        double[] up = {m[8], m[9], m[10]};

        // Each ENU axis is a unit vector and they are mutually orthogonal.
        for (double[] axis : new double[][]{east, north, up}) {
            assertEquals(1.0, axis[0] * axis[0] + axis[1] * axis[1] + axis[2] * axis[2], EPS);
        }
        assertEquals(0.0, dot(east, north), EPS);
        assertEquals(0.0, dot(east, up), EPS);
        assertEquals(0.0, dot(north, up), EPS);

        // Translation: the geodetic-to-ECEF position must sit on the local
        // vertical at distance N(1-e2)+alt-ish; sanity-check it is roughly one
        // earth radius from the origin and points the same way as "up".
        double[] t = {m[12], m[13], m[14]};
        double r = Math.sqrt(dot(t, t));
        assertTrue(r > 6.3e6 && r < 6.4e6, "translation should be ~1 earth radius, was " + r);
        // The reference point lies (almost exactly) along its own local up axis.
        double cos = dot(t, up) / r;
        assertEquals(1.0, cos, 1e-4);
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }
}
