/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.writer;

import org.citydb.model.common.Name;
import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.Envelope;
import org.citydb.model.property.GeometryProperty;
import org.citydb.vis.VisExportException;
import org.citydb.vis.config.ClampMode;
import org.citydb.vis.config.VisFormatOptions;
import org.citydb.vis.attribute.AttributeEncoder;
import org.citydb.vis.geometry.GeometryMeshBuilder;
import org.citydb.vis.terrain.TerrainElevationProvider;
import org.citydb.vis.geometry.RingAttributes;
import org.citydb.vis.geometry.TriangleMesh;
import org.citydb.vis.store.SpatialEntry;
import org.citydb.vis.store.VisExportStores;

import java.io.IOException;
import java.util.List;
import java.util.Map;

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

    public FeatureProcessor(VisExportStores stores,
                            VisFormatOptions formatOptions,
                            AttributeEncoder attributeEncoder,
                            TerrainElevationProvider terrainProvider) {
        this.stores = stores;
        this.formatOptions = formatOptions;
        this.attributeEncoder = attributeEncoder;
        this.terrainProvider = terrainProvider;
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

    public void process(long featureId, String objectId, String featureType,
                        String featureTypeNamespace,
                        Envelope envelope, Map<String, Object> attributes,
                        List<GeometryProperty> geomProps,
                        RingAttributes ringAttributes) throws VisExportException {
        // Surface type fallback for geometries that hang directly off the
        // top-level feature (e.g. a Building's own LoD1 box) — anything
        // owned by a nested boundary surface picks up that surface's type
        // inside GeometryMeshBuilder.
        Name defaultSurfaceType = Name.of(featureType, featureTypeNamespace);
        TriangleMesh mesh = GeometryMeshBuilder.build(geomProps, featureId, defaultSurfaceType,
                ringAttributes);
        if (mesh.isEmpty()) {
            return;
        }

        Envelope env = envelope;
        ClampMode clampMode = formatOptions.getClampMode();
        if (clampMode != null) {
            // Ground height the mesh's lowest point is shifted onto: 0 for the
            // ellipsoid, or the Cesium World Terrain height sampled at the
            // feature centroid (lon/lat — the export CRS is WGS84, X=lon Y=lat).
            // A failed terrain sample (NaN) falls back to the ellipsoid.
            double groundHeight = 0.0;
            if (clampMode == ClampMode.CESIUM_WORLD_TERRAIN && terrainProvider != null) {
                double[] centroid = centroidLonLat(env, mesh);
                double sampled = terrainProvider.sampleHeight(centroid[0], centroid[1]);
                if (!Double.isNaN(sampled)) {
                    groundHeight = sampled;
                }
            }
            mesh.clampToGround(groundHeight);
            // Recompute Z from the clamped mesh — the Feature's envelope Z
            // may not match the mesh when multiple LODs or non-surface
            // geometries contribute to the envelope.
            if (env != null) {
                double[] meshBbox = mesh.computeBoundingBox();
                env = Envelope.of(
                        Coordinate.of(env.getLowerCorner().getX(),
                                env.getLowerCorner().getY(), meshBbox[2]),
                        Coordinate.of(env.getUpperCorner().getX(),
                                env.getUpperCorner().getY(), meshBbox[5]));
            }
        }

        double cx, cy;
        double[] bbox;
        if (env != null) {
            cx = (env.getLowerCorner().getX() + env.getUpperCorner().getX()) / 2;
            cy = (env.getLowerCorner().getY() + env.getUpperCorner().getY()) / 2;
            bbox = new double[]{
                    env.getLowerCorner().getX(), env.getLowerCorner().getY(),
                    env.getLowerCorner().getZ(),
                    env.getUpperCorner().getX(), env.getUpperCorner().getY(),
                    env.getUpperCorner().getZ()
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
}
