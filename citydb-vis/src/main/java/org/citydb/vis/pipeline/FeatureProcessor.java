/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.pipeline;

import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.Envelope;
import org.citydb.model.appearance.TextureCoordinate;
import org.citydb.model.geometry.LinearRing;
import org.citydb.model.property.GeometryProperty;
import org.citydb.vis.writer.VisExportException;
import org.citydb.vis.encoder.AttributeEncoder;
import org.citydb.vis.geometry.GeometryMeshBuilder;
import org.citydb.vis.geometry.TriangleMesh;
import org.citydb.vis.store.SpatialEntry;
import org.citydb.vis.store.VisExportStores;
import org.citydb.vis.writer.VisFormatOptions;

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

    public FeatureProcessor(VisExportStores stores,
                            VisFormatOptions formatOptions,
                            AttributeEncoder attributeEncoder) {
        this.stores = stores;
        this.formatOptions = formatOptions;
        this.attributeEncoder = attributeEncoder;
    }

    public void process(long featureId, String objectId, String featureType,
                        Envelope envelope, Map<String, Object> attributes,
                        List<GeometryProperty> geomProps,
                        Map<LinearRing, List<TextureCoordinate>> texCoords,
                        Map<LinearRing, Integer> ringTextureIds) throws VisExportException {
        TriangleMesh mesh = GeometryMeshBuilder.build(geomProps, featureId,
                texCoords, ringTextureIds);
        if (mesh.isEmpty()) {
            return;
        }

        Envelope env = envelope;
        if (formatOptions.isClampToGround()) {
            mesh.clampToGround();
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
