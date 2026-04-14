/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.scene;

/**
 * Represents a Minimum Bounding Sphere (MBS).
 * Format: [centerX (lon), centerY (lat), centerZ (altitude m), radius (m)]
 * <p>
 * For EPSG:4326 the center is in (longitude, latitude, altitude) but the
 * radius must be in meters. Degree-to-meter conversion uses:
 * - 1° latitude  ≈ 111,320 m
 * - 1° longitude ≈ 111,320 * cos(latitude) m
 */
public class BoundingVolume {
    private static final double METERS_PER_DEGREE_LAT = 111_320.0;

    private final double centerX;
    private final double centerY;
    private final double centerZ;
    private final double radius;

    private BoundingVolume(double centerX, double centerY, double centerZ, double radius) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.radius = radius;
    }

    /**
     * Create MBS from an axis-aligned bounding box in geographic coordinates (EPSG:4326).
     * Converts degree extents to meters for the radius computation.
     */
    public static BoundingVolume ofBoundingBox(double minX, double minY, double minZ,
                                               double maxX, double maxY, double maxZ) {
        double cx = (minX + maxX) / 2;
        double cy = (minY + maxY) / 2;
        double cz = (minZ + maxZ) / 2;

        // Convert degree differences to meters
        double dxMeters = (maxX - minX) * METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(cy));
        double dyMeters = (maxY - minY) * METERS_PER_DEGREE_LAT;
        double dzMeters = maxZ - minZ;

        double radius = Math.sqrt(dxMeters * dxMeters + dyMeters * dyMeters + dzMeters * dzMeters) / 2;
        return new BoundingVolume(cx, cy, cz, radius);
    }

    public double getCenterX() {
        return centerX;
    }

    public double getCenterY() {
        return centerY;
    }

    public double getCenterZ() {
        return centerZ;
    }

    public double getRadius() {
        return radius;
    }

    public double[] toMbs() {
        return new double[]{centerX, centerY, centerZ, radius};
    }

    /**
     * Merge two bounding spheres. Distance between centers is computed in meters
     * for geographic coordinates (EPSG:4326).
     */
    public BoundingVolume merge(BoundingVolume other) {
        if (radius == 0) return other;
        if (other.radius == 0) return this;

        // Convert center difference to meters for distance computation
        double midLat = (centerY + other.centerY) / 2;
        double dxMeters = (other.centerX - centerX) * METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(midLat));
        double dyMeters = (other.centerY - centerY) * METERS_PER_DEGREE_LAT;
        double dzMeters = other.centerZ - centerZ;
        double dist = Math.sqrt(dxMeters * dxMeters + dyMeters * dyMeters + dzMeters * dzMeters);

        if (dist + other.radius <= radius) return this;
        if (dist + radius <= other.radius) return other;

        double newRadius = (dist + radius + other.radius) / 2;
        double ratio = dist > 0 ? (newRadius - radius) / dist : 0;

        // Interpolate center in degree space
        return new BoundingVolume(
                centerX + (other.centerX - centerX) * ratio,
                centerY + (other.centerY - centerY) * ratio,
                centerZ + dzMeters * ratio,
                newRadius);
    }
}
