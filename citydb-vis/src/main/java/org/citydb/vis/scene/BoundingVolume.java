/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.scene;

/**
 * Axis-aligned bounding volume in EPSG:4326 geographic coordinates.
 * <p>
 * Stores the source AABB (min/max in degrees/meters) and derives both
 * MBS (Minimum Bounding Sphere) for CesiumJS and OBB (Oriented Bounding
 * Box) for ArcGIS Pro on demand.
 * <p>
 * For EPSG:4326 the center is in (longitude, latitude, altitude) but
 * sizes and radii are in meters. Degree-to-meter conversion uses:
 * - 1° latitude  ≈ 111,320 m
 * - 1° longitude ≈ 111,320 * cos(latitude) m
 */
public class BoundingVolume {
    private static final double METERS_PER_DEGREE_LAT = 111_320.0;

    // Source AABB (degrees for X/Y, meters for Z)
    private final double minX, minY, minZ;
    private final double maxX, maxY, maxZ;

    // Derived MBS
    private final double centerX, centerY, centerZ;
    private final double radius;

    private BoundingVolume(double minX, double minY, double minZ,
                           double maxX, double maxY, double maxZ,
                           double centerX, double centerY, double centerZ,
                           double radius) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.radius = radius;
    }

    /**
     * Create a bounding volume from an axis-aligned bounding box in
     * geographic coordinates (EPSG:4326).
     */
    public static BoundingVolume ofBoundingBox(double minX, double minY, double minZ,
                                               double maxX, double maxY, double maxZ) {
        double cx = (minX + maxX) / 2;
        double cy = (minY + maxY) / 2;
        double cz = (minZ + maxZ) / 2;

        double dxMeters = (maxX - minX) * METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(cy));
        double dyMeters = (maxY - minY) * METERS_PER_DEGREE_LAT;
        double dzMeters = maxZ - minZ;

        double radius = Math.sqrt(dxMeters * dxMeters + dyMeters * dyMeters + dzMeters * dzMeters) / 2;
        return new BoundingVolume(minX, minY, minZ, maxX, maxY, maxZ,
                cx, cy, cz, radius);
    }

    // ---- AABB accessors ----------------------------------------------------

    public double getMinX() { return minX; }
    public double getMinY() { return minY; }
    public double getMinZ() { return minZ; }
    public double getMaxX() { return maxX; }
    public double getMaxY() { return maxY; }
    public double getMaxZ() { return maxZ; }

    // ---- MBS accessors (CesiumJS / I3S) ------------------------------------

    public double getCenterX() { return centerX; }
    public double getCenterY() { return centerY; }
    public double getCenterZ() { return centerZ; }
    public double getRadius() { return radius; }

    /** MBS array: [centerX (lon), centerY (lat), centerZ (alt m), radius (m)]. */
    public double[] toMbs() {
        return new double[]{centerX, centerY, centerZ, radius};
    }

    // ---- OBB accessors (ArcGIS / I3S) --------------------------------------

    /** OBB center: [lon°, lat°, alt m]. */
    public double[] toObbCenter() {
        return new double[]{centerX, centerY, centerZ};
    }

    /**
     * OBB half-size in meters along each axis.
     * Since the bounding box is axis-aligned, this is simply the AABB
     * half-extents converted from degrees to meters.
     */
    public double[] toObbHalfSize() {
        double halfX = (maxX - minX) / 2 * METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(centerY));
        double halfY = (maxY - minY) / 2 * METERS_PER_DEGREE_LAT;
        double halfZ = (maxZ - minZ) / 2;
        return new double[]{halfX, halfY, halfZ};
    }

    /** OBB quaternion: identity (no rotation) since the box is axis-aligned. */
    public double[] toObbQuaternion() {
        return new double[]{0, 0, 0, 1};
    }

    // ---- Merge -------------------------------------------------------------

    /**
     * Merge two bounding volumes by merging their AABBs and recomputing
     * the MBS from the merged AABB.
     */
    public BoundingVolume merge(BoundingVolume other) {
        if (radius == 0) return other;
        if (other.radius == 0) return this;

        return ofBoundingBox(
                Math.min(minX, other.minX), Math.min(minY, other.minY),
                Math.min(minZ, other.minZ),
                Math.max(maxX, other.maxX), Math.max(maxY, other.maxY),
                Math.max(maxZ, other.maxZ));
    }
}
