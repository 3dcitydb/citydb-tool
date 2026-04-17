/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.encoder.tiles3d;

import org.citydb.vis.encoder.AttrValueCoercer;
import org.citydb.vis.geometry.TriangleMesh;
import org.citydb.vis.geometry.VertexWelder;
import org.citydb.vis.model.AttrField;
import org.citydb.vis.model.FeatureData;
import org.citydb.vis.scene.BoundingVolume;
import org.citydb.vis.scene.SceneNode;
import org.citydb.vis.util.GeoTransform;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Encodes a scene node's geometry, textures, and per-feature metadata into a
 * glTF 2.0 Binary (GLB) file without external library dependencies.
 * <p>
 * Each GLB contains:
 * <ul>
 *   <li>Mesh geometry: positions (local ENU), normals, optional UVs, indices</li>
 *   <li>Texture: JPEG atlas embedded as buffer view (if textured)</li>
 *   <li>{@code EXT_mesh_features}: per-vertex feature ID attribute</li>
 *   <li>{@code EXT_structural_metadata}: per-feature property table</li>
 * </ul>
 * <p>
 * Positions are in a local ENU coordinate frame relative to a dataset center.
 * The root tile's {@code transform} in {@code tileset.json} converts ENU to ECEF.
 */
public class GlbEncoder {
    /**
     * Encode a mesh node into a GLB byte array. Returns {@code null} if the
     * mesh is empty after welding/degenerate filtering.
     *
     * @param node          scene node (mesh will be cleared after encoding)
     * @param textureBytes  JPEG atlas bytes, or {@code null} if untextured
     * @param features      per-feature attribute data
     * @param attrFields    finalized attribute field definitions
     * @param datasetCenter [centerLon, centerLat, centerAlt] of the dataset
     * @return GLB bytes, or {@code null} if empty
     */
    public byte[] encode(SceneNode node, byte[] textureBytes,
                         List<FeatureData> features, List<AttrField> attrFields,
                         double[] datasetCenter) throws IOException {
        TriangleMesh mesh = node.getMesh();
        boolean hasTexCoords = mesh.hasTexCoords() && textureBytes != null;
        if (!hasTexCoords) {
            node.setTextured(false);
        }

        BoundingVolume mbs = node.getBoundingVolume();
        VertexWelder.WeldResult weld = VertexWelder.weldAndFilter(mesh,
                mbs.getCenterX(), mbs.getCenterY(), mbs.getCenterZ());
        node.setOutputVertexCount(weld.vertexCount());
        if (weld.isEmpty()) {
            node.setMesh(null);
            return null;
        }

        DatasetFrame frame = DatasetFrame.from(mbs, datasetCenter);
        VertexArrays arrays = buildVertexArrays(mesh, weld, hasTexCoords, frame);
        node.setMesh(null); // release source mesh; no longer needed

        BinBufferBuilder bin = new BinBufferBuilder();
        BufferViewIds bvIds = writeGeometryBuffers(bin, arrays, hasTexCoords, textureBytes);

        int featureCount = weld.faceRanges().size();
        List<FeatureData> propFeatures = featureCount < features.size()
                ? FeatureData.reorderByIds(features, weld.rangeFeatureIds())
                : features;
        List<PropertyTableBufferViews> propBvs = new ArrayList<>();
        for (AttrField field : attrFields) {
            propBvs.add(encodePropertyField(bin, field, propFeatures));
        }

        byte[] binData = bin.toByteArray();
        byte[] jsonData = new GltfJsonBuilder()
                .geometry(arrays.vertexCount, arrays.posMin, arrays.posMax, hasTexCoords)
                .bufferViews(bin.getBufferViews(), binData.length)
                .geometryAccessors(bvIds.positions, bvIds.normals, bvIds.uvs,
                        bvIds.indices, bvIds.featureIds, bvIds.texture)
                .metadata(featureCount, attrFields, propBvs)
                .build();

        return GlbContainer.assemble(jsonData, binData);
    }

    /**
     * Rewrites welded vertex data from ENU (meters, East/North/Up) into glTF Y-up
     * (X=East, Y=Up, Z=-North), collects per-axis min/max for the POSITION
     * accessor, and selects normals/UVs per the texturing mode.
     */
    private static VertexArrays buildVertexArrays(TriangleMesh mesh, VertexWelder.WeldResult weld,
                                                  boolean hasTexCoords, DatasetFrame frame) {
        int vertexCount = weld.vertexCount();

        // Untextured nodes carry normals; textured nodes let CesiumJS generate
        // flat normals from geometry (matches I3S behaviour, better for buildings).
        float[] positions = new float[vertexCount * 3];
        float[] normals = hasTexCoords ? null : new float[vertexCount * 3];
        float[] uvs = hasTexCoords ? new float[vertexCount * 2] : null;
        int[] indices = new int[vertexCount]; // triangle soup: 0,1,2,3,...
        float[] posMin = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] posMax = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};

        VertexWelder.iterateOutputVertices(weld, mesh, (idx, wp, srcIdx) -> {
            float east = wp[0] * (float) frame.scaleX + frame.offsetX;
            float north = wp[1] * (float) frame.scaleY + frame.offsetY;
            float up = wp[2] + frame.offsetZ;
            positions[idx * 3] = east;
            positions[idx * 3 + 1] = up;
            positions[idx * 3 + 2] = -north;
            posMin[0] = Math.min(posMin[0], east);
            posMin[1] = Math.min(posMin[1], up);
            posMin[2] = Math.min(posMin[2], -north);
            posMax[0] = Math.max(posMax[0], east);
            posMax[1] = Math.max(posMax[1], up);
            posMax[2] = Math.max(posMax[2], -north);

            if (normals != null) {
                float[] n = mesh.getNormals().get(srcIdx);
                normals[idx * 3] = n[0];
                normals[idx * 3 + 1] = n[2];
                normals[idx * 3 + 2] = -n[1];
            }
            if (uvs != null) {
                float[] uv = mesh.getTexCoords().get(srcIdx);
                uvs[idx * 2] = uv[0];
                uvs[idx * 2 + 1] = uv[1];
            }
            indices[idx] = idx;
        });

        return new VertexArrays(vertexCount, positions, normals, uvs,
                indices, weld.computeFeatureIndices(), posMin, posMax);
    }

    /** Writes geometry arrays into the BIN buffer and returns their buffer view ids. */
    private static BufferViewIds writeGeometryBuffers(BinBufferBuilder bin, VertexArrays a,
                                                      boolean hasTexCoords, byte[] textureBytes) {
        int bvPositions = bin.addFloat32Array(a.positions);
        int bvNormals = a.normals != null ? bin.addFloat32Array(a.normals) : -1;
        int bvUvs = hasTexCoords ? bin.addFloat32Array(a.uvs) : -1;
        int bvIndices = bin.addUint32Array(a.indices);
        int bvFeatureIds = bin.addUint32Array(a.featureIds);
        int bvTexture = (textureBytes != null && hasTexCoords) ? bin.addRawBytes(textureBytes) : -1;
        return new BufferViewIds(bvPositions, bvNormals, bvUvs, bvIndices, bvFeatureIds, bvTexture);
    }

    /**
     * Scale/offset from node-local meters to dataset-centered ENU meters.
     * Welded positions are relative to the node center; the offset shifts them
     * into the dataset-centered frame used by the root tile's ENU-to-ECEF transform.
     */
    private record DatasetFrame(double scaleX, double scaleY,
                                float offsetX, float offsetY, float offsetZ) {
        static DatasetFrame from(BoundingVolume mbs, double[] datasetCenter) {
            double scaleX = GeoTransform.metersPerDegreeLon(datasetCenter[1]);
            double scaleY = GeoTransform.WGS84_METERS_PER_DEGREE_LAT;
            float offsetX = (float) ((mbs.getCenterX() - datasetCenter[0]) * scaleX);
            float offsetY = (float) ((mbs.getCenterY() - datasetCenter[1]) * scaleY);
            float offsetZ = (float) (mbs.getCenterZ() - datasetCenter[2]);
            return new DatasetFrame(scaleX, scaleY, offsetX, offsetY, offsetZ);
        }
    }

    private record VertexArrays(int vertexCount, float[] positions, float[] normals, float[] uvs,
                                int[] indices, int[] featureIds, float[] posMin, float[] posMax) {
    }

    private record BufferViewIds(int positions, int normals, int uvs,
                                 int indices, int featureIds, int texture) {
    }

    // ---- Property table encoding ----------------------------------------

    private static PropertyTableBufferViews encodePropertyField(
            BinBufferBuilder bin, AttrField field, List<FeatureData> features) {
        String name = field.name();
        return switch (field.type()) {
            case OID, INT -> new PropertyTableBufferViews(
                    bin.addInt32Array(AttrValueCoercer.extractInts(features, name)), -1);
            case DOUBLE -> new PropertyTableBufferViews(
                    bin.addFloat64Array(AttrValueCoercer.extractDoubles(features, name)), -1);
            case STRING -> encodeStringProperty(bin, name, features);
        };
    }

    private static PropertyTableBufferViews encodeStringProperty(
            BinBufferBuilder bin, String fieldName, List<FeatureData> features) {
        byte[][] utf8 = AttrValueCoercer.extractUtf8(features, fieldName);
        ByteArrayOutputStream valuesStream = new ByteArrayOutputStream();
        int[] offsets = new int[utf8.length + 1];
        int offset = 0;
        for (int i = 0; i < utf8.length; i++) {
            offsets[i] = offset;
            valuesStream.writeBytes(utf8[i]);
            offset += utf8[i].length;
        }
        offsets[utf8.length] = offset;

        int valuesBv = bin.addRawBytes(valuesStream.toByteArray());
        int offsetsBv = bin.addUint32Array(offsets);
        return new PropertyTableBufferViews(valuesBv, offsetsBv);
    }
}
