/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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

    public static double[] enuToEcefMatrix(double[] center) {
        double lonRad = Math.toRadians(center[0]);
        double latRad = Math.toRadians(center[1]);
        double alt = center[2];

        double sinLon = Math.sin(lonRad);
        double cosLon = Math.cos(lonRad);
        double sinLat = Math.sin(latRad);
        double cosLat = Math.cos(latRad);

        double N = WGS84_A / Math.sqrt(1 - WGS84_E2 * sinLat * sinLat);

        double x = (N + alt) * cosLat * cosLon;
        double y = (N + alt) * cosLat * sinLon;
        double z = (N * (1 - WGS84_E2) + alt) * sinLat;

        return new double[]{
                -sinLon, cosLon, 0, 0,
                -sinLat * cosLon, -sinLat * sinLon, cosLat, 0,
                cosLat * cosLon, cosLat * sinLon, sinLat, 0,
                x, y, z, 1
        };
    }
}
