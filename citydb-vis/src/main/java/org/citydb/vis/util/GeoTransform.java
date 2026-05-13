/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.util;

public class GeoTransform {
    public static final double WGS84_METERS_PER_DEGREE_LAT = 111_320.0;
    public static final double WGS84_A = 6_378_137.0;
    public static final double WGS84_E2 = 0.00669437999014;

    private GeoTransform() {
    }

    public static double metersPerDegreeLon(double latDeg) {
        return WGS84_METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(latDeg));
    }

    /**
     * Precomputed sin/cos of longitude and latitude for a reference point,
     * together with the rotation that maps an ENU-basis vector at that point
     * into ECEF.
     */
    public record EnuBasis(double sinLon, double cosLon, double sinLat, double cosLat) {
        public static EnuBasis at(double centerLonDeg, double centerLatDeg) {
            double lonRad = Math.toRadians(centerLonDeg);
            double latRad = Math.toRadians(centerLatDeg);
            return new EnuBasis(Math.sin(lonRad), Math.cos(lonRad),
                    Math.sin(latRad), Math.cos(latRad));
        }

        /**
         * Rotate an ENU-basis normal (east, north, up) into ECEF, writing the
         * result to {@code out}. Only the 3x3 rotation is applied — pure
         * normal rotation, no translation.
         */
        public void rotateNormalToEcef(float[] enuNormal, float[] out) {
            double ne = enuNormal[0], nn = enuNormal[1], nu = enuNormal[2];
            out[0] = (float) (-sinLon * ne - sinLat * cosLon * nn + cosLat * cosLon * nu);
            out[1] = (float) (cosLon * ne - sinLat * sinLon * nn + cosLat * sinLon * nu);
            out[2] = (float) (cosLat * nn + sinLat * nu);
        }

        /**
         * Write the ECEF unit vector pointing along the ENU "up" axis at this
         * basis's reference point — i.e. the local vertical (radially outward
         * from the WGS84 ellipsoid center). Specialised form of
         * {@link #rotateNormalToEcef} with input {@code (0, 0, 1)}; precomputed
         * to avoid the redundant multiplications.
         */
        public void fillUpInEcef(float[] out) {
            out[0] = (float) (cosLat * cosLon);
            out[1] = (float) (cosLat * sinLon);
            out[2] = (float) sinLat;
        }

        /**
         * Unit quaternion (x, y, z, w) representing the rotation that maps an
         * ENU-aligned local frame at this basis's reference point into ECEF
         * world coordinates. Used by the I3S OBB encoder so that a box with
         * half-extents along ENU (east / north / up) is correctly oriented
         * in ECEF for all I3S clients (Cesium, ArcGIS Pro, ArcGIS Maps SDK).
         * <p>
         * Derived by matrix→quaternion conversion of the 3×3 rotation whose
         * columns are the ENU basis vectors in ECEF (same convention as
         * {@link GeoTransform#enuToEcefMatrix}; the rotation matches Esri's
         * own samples like NYC where every per-node OBB quaternion is non-
         * identity even though the box is axis-aligned with local ENU).
         */
        public double[] enuToEcefQuaternion() {
            // M = [east | north | up] (columns) in ECEF.
            double m00 = -sinLon,            m01 = -sinLat * cosLon, m02 = cosLat * cosLon;
            double m10 =  cosLon,            m11 = -sinLat * sinLon, m12 = cosLat * sinLon;
            double m20 =  0,                 m21 =  cosLat,          m22 = sinLat;
            // Shepperd / Shoemake matrix-to-quaternion: pick the branch that
            // maximises the divisor so the result stays well-conditioned at
            // all latitudes (the trace-positive branch alone collapses near
            // the equator / poles for this particular rotation family).
            double qx, qy, qz, qw;
            double trace = m00 + m11 + m22;
            if (trace > 0) {
                double s = Math.sqrt(trace + 1.0) * 2;
                qw = 0.25 * s;
                qx = (m21 - m12) / s;
                qy = (m02 - m20) / s;
                qz = (m10 - m01) / s;
            } else if (m00 > m11 && m00 > m22) {
                double s = Math.sqrt(1.0 + m00 - m11 - m22) * 2;
                qw = (m21 - m12) / s;
                qx = 0.25 * s;
                qy = (m01 + m10) / s;
                qz = (m02 + m20) / s;
            } else if (m11 > m22) {
                double s = Math.sqrt(1.0 + m11 - m00 - m22) * 2;
                qw = (m02 - m20) / s;
                qx = (m01 + m10) / s;
                qy = 0.25 * s;
                qz = (m12 + m21) / s;
            } else {
                double s = Math.sqrt(1.0 + m22 - m00 - m11) * 2;
                qw = (m10 - m01) / s;
                qx = (m02 + m20) / s;
                qy = (m12 + m21) / s;
                qz = 0.25 * s;
            }
            return new double[]{qx, qy, qz, qw};
        }
    }

    public static double[] enuToEcefMatrix(double[] center) {
        EnuBasis b = EnuBasis.at(center[0], center[1]);
        double alt = center[2];
        double N = WGS84_A / Math.sqrt(1 - WGS84_E2 * b.sinLat * b.sinLat);

        double x = (N + alt) * b.cosLat * b.cosLon;
        double y = (N + alt) * b.cosLat * b.sinLon;
        double z = (N * (1 - WGS84_E2) + alt) * b.sinLat;

        return new double[]{
                -b.sinLon, b.cosLon, 0, 0,
                -b.sinLat * b.cosLon, -b.sinLat * b.sinLon, b.cosLat, 0,
                b.cosLat * b.cosLon, b.cosLat * b.sinLon, b.sinLat, 0,
                x, y, z, 1
        };
    }
}
