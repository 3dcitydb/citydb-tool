/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.scene;

import org.citydb.vis.util.GeoTransform;

/**
 * Axis-aligned bounding volume in EPSG:4326 geographic coordinates.
 * <p>
 * Stores the source AABB (min/max in degrees/meters) and derives both
 * MBS (Minimum Bounding Sphere) and OBB (Oriented Bounding Box) on demand.
 * OBB is emitted only for SLPK/ArcGIS output (required by I3S 1.7 schema);
 * plain folder output for CesiumJS uses MBS only, because CesiumJS's I3S
 * OBB handling mis-culls buildings at some camera angles.
 * <p>
 * For EPSG:4326 the center is in (longitude, latitude, altitude) but
 * sizes and radii are in meters. Degree-to-meter conversion uses:
 * - 1° latitude  ≈ 111,320 m
 * - 1° longitude ≈ 111,320 * cos(latitude) m
 */
public class BoundingVolume {
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

        double dxMeters = (maxX - minX) * GeoTransform.metersPerDegreeLon(cy);
        double dyMeters = (maxY - minY) * GeoTransform.WGS84_METERS_PER_DEGREE_LAT;
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

    // ---- MBS accessors -----------------------------------------------------

    public double getCenterX() { return centerX; }
    public double getCenterY() { return centerY; }
    public double getCenterZ() { return centerZ; }
    public double getRadius() { return radius; }

    /** MBS array: [centerX (lon), centerY (lat), centerZ (alt m), radius (m)]. */
    public double[] toMbs() {
        return new double[]{centerX, centerY, centerZ, radius};
    }

    // ---- OBB accessors -----------------------------------------------------

    /** OBB center: [lon°, lat°, alt m]. */
    public double[] toObbCenter() {
        return new double[]{centerX, centerY, centerZ};
    }

    /**
     * OBB half-size in meters along the OBB's local axes (east, north, up).
     * Since the box is axis-aligned in geographic coordinates, half-extents
     * are simply the AABB half-widths converted from degrees to meters.
     */
    public double[] toObbHalfSize() {
        double halfX = (maxX - minX) / 2 * GeoTransform.metersPerDegreeLon(centerY);
        double halfY = (maxY - minY) / 2 * GeoTransform.WGS84_METERS_PER_DEGREE_LAT;
        double halfZ = (maxZ - minZ) / 2;
        return new double[]{halfX, halfY, halfZ};
    }

    /** OBB quaternion: identity — the box is axis-aligned with local ENU. */
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
