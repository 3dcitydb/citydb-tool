/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.writer;

import org.citydb.model.common.Matrix4x4;
import org.citydb.model.common.Name;
import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.Envelope;
import org.citydb.model.geometry.Point;
import org.citydb.model.property.GeometryProperty;
import org.citydb.vis.VisExportException;
import org.citydb.vis.config.ClampMode;
import org.citydb.vis.config.VisFormatOptions;
import org.citydb.vis.attribute.AttributeEncoder;
import org.citydb.vis.geometry.GeometryMeshBuilder;
import org.citydb.vis.geometry.TrsDecomposition;
import org.citydb.vis.terrain.TerrainElevationProvider;
import org.citydb.vis.geometry.RingAttributes;
import org.citydb.vis.geometry.TriangleMesh;
import org.citydb.vis.store.InstanceStore;
import org.citydb.vis.store.SpatialEntry;
import org.citydb.vis.store.VisExportStores;
import org.citydb.vis.util.BoundingBoxUtils;
import org.citydb.vis.util.GeoTransform;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Triangulates a feature's geometry, optionally clamps it to ground, computes
 * spatial metadata, and persists mesh + attributes + spatial entry to the
 * disk-backed stores. Called once per feature from the writer's async
 * processing pool.
 */
public final class FeatureProcessor {
    private final VisExportStores stores;
    private final VisFormatOptions formatOptions;
    private final AttributeEncoder attributeEncoder;
    // Non-null only for --clamp-to-ground=cesium-world-terrain; samples the
    // terrain height baked into each feature. Must be thread-safe (this
    // processor runs once per feature on the writer's async pool).
    private final TerrainElevationProvider terrainProvider;
    // Writer hook for --outline-merge-coplanar (null = off, else the maximum
    // dihedral angle in degrees), evaluated per process() call: a supplier
    // rather than the resolved value because this processor is built in the
    // VisWriter constructor, before the writer subclass that overrides the
    // hook is fully constructed.
    private final Supplier<Double> mergeCoplanarOutlineAngle;

    public FeatureProcessor(VisExportStores stores,
                            VisFormatOptions formatOptions,
                            AttributeEncoder attributeEncoder,
                            TerrainElevationProvider terrainProvider,
                            Supplier<Double> mergeCoplanarOutlineAngle) {
        this.stores = stores;
        this.formatOptions = formatOptions;
        this.attributeEncoder = attributeEncoder;
        this.terrainProvider = terrainProvider;
        this.mergeCoplanarOutlineAngle = mergeCoplanarOutlineAngle;
    }

    /**
     * Feature centroid in WGS84 lon/lat (degrees), taken from the envelope when
     * present (cheap) and otherwise from the mesh bounds. Z is irrelevant here
     * and ignored. Used as the terrain sampling location.
     */
    private static double[] centroidLonLat(Envelope env, TriangleMesh mesh) {
        if (env != null) {
            Coordinate center = env.getCenter();
            return new double[]{center.getX(), center.getY()};
        }
        double[] bbox = mesh.computeBoundingBox();
        return new double[]{(bbox[0] + bbox[3]) / 2, (bbox[1] + bbox[4]) / 2};
    }

    public void process(long featureId, String objectId, String featureLabel,
                        String featureType, String featureTypeNamespace,
                        Envelope envelope, Map<String, Object> attributes,
                        List<GeometryProperty> geomProps,
                        RingAttributes ringAttributes) throws VisExportException {
        // Surface type fallback for geometries that hang directly off the
        // top-level feature (e.g. a Building's own LoD1 box) — anything
        // owned by a nested boundary surface picks up that surface's type
        // inside GeometryMeshBuilder.
        Name defaultSurfaceType = Name.of(featureType, featureTypeNamespace);
        TriangleMesh mesh = GeometryMeshBuilder.build(geomProps, featureId, defaultSurfaceType,
                ringAttributes, featureLabel, false);
        if (mesh.isEmpty()) {
            return;
        }
        Double mergeMaxAngle = mergeCoplanarOutlineAngle.get();
        if (mergeMaxAngle != null) {
            // After build (T-junctions resolved, duplicates removed) so edge
            // matching runs on the final geometry; degree-to-meter scales as
            // in the T-junction pass — coplanarity must be tested in meters.
            double[] center = mesh.computeCenter();
            mesh.mergeCoplanarOutlineEdges(GeoTransform.metersPerDegreeLon(center[1]),
                    GeoTransform.WGS84_METERS_PER_DEGREE_LAT, mergeMaxAngle);
        }

        ClampMode clampMode = formatOptions.getClampMode();
        boolean clamped = clampMode != null;
        if (clamped) {
            // Ground height the mesh's lowest point is shifted onto: 0 for the
            // ellipsoid, or the Cesium World Terrain height sampled at the
            // feature centroid (lon/lat — the export CRS is WGS84, X=lon Y=lat).
            // A failed terrain sample (NaN) falls back to the ellipsoid.
            double groundHeight = 0.0;
            if (clampMode == ClampMode.CESIUM_WORLD_TERRAIN && terrainProvider != null) {
                double[] centroid = centroidLonLat(envelope, mesh);
                double sampled = terrainProvider.sampleHeight(centroid[0], centroid[1]);
                if (!Double.isNaN(sampled)) {
                    groundHeight = sampled;
                }
            }
            mesh.clampToGround(groundHeight);
        }

        // Spatial bbox for partitioning. The DB envelope is a cheap proxy for
        // the mesh bounds, used only when we haven't already paid for the mesh
        // box: when clamping we compute it anyway (the shift moves Z, and the
        // Feature's envelope may not match the mesh when multiple LODs or
        // non-surface geometries contribute to it), so use that ground-truth
        // box directly for both XY and Z instead of trusting the envelope.
        double cx, cy;
        double[] bbox;
        if (!clamped && envelope != null) {
            cx = (envelope.getLowerCorner().getX() + envelope.getUpperCorner().getX()) / 2;
            cy = (envelope.getLowerCorner().getY() + envelope.getUpperCorner().getY()) / 2;
            bbox = new double[]{
                    envelope.getLowerCorner().getX(), envelope.getLowerCorner().getY(),
                    envelope.getLowerCorner().getZ(),
                    envelope.getUpperCorner().getX(), envelope.getUpperCorner().getY(),
                    envelope.getUpperCorner().getZ()
            };
        } else {
            bbox = mesh.computeBoundingBox();
            cx = (bbox[0] + bbox[3]) / 2;
            cy = (bbox[1] + bbox[4]) / 2;
        }

        try {
            long meshHandle = stores.getMeshStore().store(mesh, (int) featureId);
            long attrOffset = stores.getAttrStore().store(objectId, featureType, attributes);
            attributeEncoder.trackFieldTypes(attributes);

            stores.getSpatialEntryStore().store(
                    new SpatialEntry(featureId, cx, cy, bbox, meshHandle, attrOffset),
                    (int) featureId);
        } catch (IOException e) {
            throw new VisExportException("Failed to persist feature " + objectId + ".", e);
        }
    }

    /**
     * Persist one GPU-instanced implicit-geometry occurrence: no mesh is baked —
     * only the placement payload ({@link InstanceStore}) plus a
     * {@link SpatialEntry} whose {@code meshHandle} is the encoded instance
     * record index, so the instance flows through partitioning and tree
     * building like any feature. The attribute blob is shared across a
     * feature's instances; the caller passes the offset it stored once.
     * <p>
     * The instance bbox is the prototype's local AABB pushed through the
     * matrix's 3×3 part and converted to degrees at the anchor — the same
     * spherical approximation the baked path applies per vertex. Any residual
     * translation column (present when the source SRS is already 4326 and the
     * reprojector skipped anchor folding) is treated as a metric ENU offset,
     * matching {@code ImplicitInstanceTransformer.applyTransform}.
     * <p>
     * Clamp-to-ground shifts the anchor height so the instance's lowest
     * <em>mesh vertex</em> (transformed through the matrix's 3×3 part) sits
     * on the sampled ground — the exact per-instance analogue of
     * {@link TriangleMesh#clampToGround}. The rotated AABB corner would lie
     * below the true minimum for pitched/rolled instances and leave them
     * floating, so it is used only for the (deliberately conservative)
     * partitioning bbox.
     */
    public void processInstance(long instanceId, String objectId, long attrOffset,
                                PrototypeRegistry.Prototype prototype,
                                Matrix4x4 transformationMatrix,
                                TrsDecomposition.Result trs,
                                Point referencePoint,
                                float[] styleColor) throws VisExportException {
        Coordinate ref = referencePoint.getCoordinate();
        double anchorLon = ref.getX();
        double anchorLat = ref.getY();
        double anchorZ = ref.getDimension() == 3 ? ref.getZ() : 0.0;
        double metersPerDegLon = GeoTransform.metersPerDegreeLon(anchorLat);

        // Residual metric translation (zero after the reprojector has folded
        // it into the anchor; nonzero only for 4326-source databases).
        anchorLon += transformationMatrix.get(0, 3) / metersPerDegLon;
        anchorLat += transformationMatrix.get(1, 3) / GeoTransform.WGS84_METERS_PER_DEGREE_LAT;
        anchorZ += transformationMatrix.get(2, 3);

        double[] metricBounds = transformAabb(prototype.localAabb(), transformationMatrix);

        ClampMode clampMode = formatOptions.getClampMode();
        if (clampMode != null) {
            double groundHeight = 0.0;
            if (clampMode == ClampMode.CESIUM_WORLD_TERRAIN && terrainProvider != null) {
                double sampled = terrainProvider.sampleHeight(anchorLon, anchorLat);
                if (!Double.isNaN(sampled)) {
                    groundHeight = sampled;
                }
            }
            // For yaw-only matrices (zero Z-row X/Y terms — the dominant
            // upright case) the AABB corner minimum is already the exact mesh
            // minimum, so skip the per-vertex scan.
            double minZ = transformationMatrix.get(2, 0) == 0 && transformationMatrix.get(2, 1) == 0
                    ? metricBounds[2]
                    : transformedMinZ(prototype.mesh(), transformationMatrix);
            anchorZ += groundHeight - (anchorZ + minZ);
        }

        double[] bbox = new double[]{
                anchorLon + metricBounds[0] / metersPerDegLon,
                anchorLat + metricBounds[1] / GeoTransform.WGS84_METERS_PER_DEGREE_LAT,
                anchorZ + metricBounds[2],
                anchorLon + metricBounds[3] / metersPerDegLon,
                anchorLat + metricBounds[4] / GeoTransform.WGS84_METERS_PER_DEGREE_LAT,
                anchorZ + metricBounds[5]
        };
        double cx = (bbox[0] + bbox[3]) / 2;
        double cy = (bbox[1] + bbox[4]) / 2;

        try {
            long recordIndex = stores.getInstanceStore().store(new InstanceStore.InstanceRecord(
                    prototype.id(), anchorLon, anchorLat, anchorZ,
                    toFloats(trs.rotation()), toFloats(trs.scale()), styleColor));

            stores.getSpatialEntryStore().store(
                    new SpatialEntry(instanceId, cx, cy, bbox,
                            InstanceStore.toHandle(recordIndex), attrOffset),
                    (int) instanceId);
        } catch (IOException e) {
            throw new VisExportException("Failed to persist instance of feature " + objectId + ".", e);
        }
    }

    /**
     * Minimum Z over the prototype's actual vertices pushed through the
     * matrix's 3×3 part — the true lowest point of the placed instance,
     * relative to the anchor. Only the matrix's Z row is evaluated.
     */
    private static double transformedMinZ(TriangleMesh mesh, Matrix4x4 m) {
        double m20 = m.get(2, 0), m21 = m.get(2, 1), m22 = m.get(2, 2);
        double minZ = Double.POSITIVE_INFINITY;
        for (double[] pos : mesh.getPositions()) {
            double mz = m20 * pos[0] + m21 * pos[1] + m22 * pos[2];
            if (mz < minZ) {
                minZ = mz;
            }
        }
        return minZ;
    }

    /**
     * Push the prototype's local AABB through the matrix's 3×3 part and return
     * the metric ENU bounds {@code [minX, minY, minZ, maxX, maxY, maxZ]}
     * relative to the anchor (translation excluded — it's in the anchor).
     */
    private static double[] transformAabb(double[] aabb, Matrix4x4 m) {
        double[] bounds = BoundingBoxUtils.emptyAabb();
        for (int corner = 0; corner < 8; corner++) {
            double x = (corner & 1) == 0 ? aabb[0] : aabb[3];
            double y = (corner & 2) == 0 ? aabb[1] : aabb[4];
            double z = (corner & 4) == 0 ? aabb[2] : aabb[5];
            double mx = m.get(0, 0) * x + m.get(0, 1) * y + m.get(0, 2) * z;
            double my = m.get(1, 0) * x + m.get(1, 1) * y + m.get(1, 2) * z;
            double mz = m.get(2, 0) * x + m.get(2, 1) * y + m.get(2, 2) * z;
            BoundingBoxUtils.expandToPoint(bounds, mx, my, mz);
        }
        return bounds;
    }

    private static float[] toFloats(double[] values) {
        float[] result = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (float) values[i];
        }
        return result;
    }
}
