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
