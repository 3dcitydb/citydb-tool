/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.encoder.i3s;

import com.openize.drako.AttributeType;
import com.openize.drako.DataBuffer;
import com.openize.drako.DataType;
import com.openize.drako.Draco;
import com.openize.drako.DracoCompressionLevel;
import com.openize.drako.DracoEncodeOptions;
import com.openize.drako.DracoMesh;
import com.openize.drako.DrakoException;
import com.openize.drako.PointAttribute;
import com.openize.drako.Vector2;
import com.openize.drako.Vector3;
import org.citydb.vis.geometry.TriangleMesh;
import org.citydb.vis.geometry.VertexWelder;
import org.citydb.vis.scene.BoundingVolume;
import org.citydb.vis.scene.SceneNode;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Encodes I3S node meshes into the two geometryBuffers declared by
 * {@link org.citydb.vis.model.i3s.GeometryDefinition}, mirroring Esri's
 * NYC reference layout. Handles vertex welding, degenerate triangle
 * removal, and ENU-to-ECEF normal transformation.
 * <ul>
 *   <li>{@code geometries/0} — uncompressed SoA buffer (position + normal +
 *       uv0 + color per vertex, featureId + faceRange per feature). Required
 *       by ArcGIS Pro for single-feature identify / picking.</li>
 *   <li>{@code geometries/1} — Draco-compressed buffer. Attributes for
 *       untextured nodes are {@code position, normal, feature-index};
 *       textured nodes use {@code position, uv0, feature-index} with
 *       normals regenerated at render time by CesiumJS.</li>
 * </ul>
 */
public class I3SGeometryEncoder {
    /**
     * Unique ID for the feature-index Draco attribute (used for metadata injection).
     * Must match the attribute's actual index in the Draco file.
     * - Without UV: POSITION(0), NORMAL(1), GENERIC(2) → uniqueId=2
     * - With UV:    POSITION(0), TEX_COORD(1), GENERIC(2) → uniqueId=2
     *   (Normal omitted from Draco when textured — CesiumJS generates normals)
     */
    private static final short FEATURE_INDEX_UID = 2;
    /** Draco header size: "DRACO" (5) + major (1) + minor (1) + type (1) + method (1) + flags (2) */
    private static final int DRACO_HEADER_SIZE = 11;
    /** Bit in the Draco header flags field that indicates metadata is present. */
    private static final short DRACO_METADATA_FLAG = (short) 0x8000;

    /**
     * Encode and write a node's geometry to {@code geometries/0} (uncompressed,
     * used by ArcGIS Pro for per-feature picking) and {@code geometries/1}
     * (Draco, used for rendering).
     *
     * @return ordered list of featureIds per face range if a geometry file was
     *         written, {@code null} if welding/degenerate filtering left the
     *         mesh empty (caller should treat this node as a non-mesh node).
     *         The caller must use this list to align per-node feature/attribute
     *         output with the Draco feature-index attribute.
     */
    public List<Long> writeNodeGeometry(Path layerDir, SceneNode node) throws IOException {
        TriangleMesh mesh = node.getMesh();
        boolean hasTexCoords = mesh.hasTexCoords();

        // Sync node texture state with actual mesh data: if mesh has no UVs,
        // clear the texture flag so node pages reference the correct geometry
        // definition (0=no uv0 vs 1=with uv0).
        if (!hasTexCoords) {
            node.setTextureId(-1);
        }

        BoundingVolume mbs = node.getBoundingVolume();
        double centerX = mbs != null ? mbs.getCenterX() : 0;
        double centerY = mbs != null ? mbs.getCenterY() : 0;
        double centerZ = mbs != null ? mbs.getCenterZ() : 0;

        VertexWelder.WeldResult weld = VertexWelder.weldAndFilter(mesh, centerX, centerY, centerZ);
        node.setOutputVertexCount(weld.vertexCount());
        if (weld.isEmpty()) {
            node.setMesh(null);
            return null;
        }

        int vertexCount = weld.vertexCount();
        List<Integer> validTriIndices = weld.validTriIndices();
        float[][] weldedPositions = weld.weldedPositions();
        List<int[]> allTriangles = mesh.getTriangles();

        // Collect positions, ECEF normals, and UVs into arrays for Draco
        // encoding and the uncompressed buffer. Normals are required by the
        // I3S defaultGeometrySchema (ordering: position, normal, uv0, color)
        // that ArcGIS Pro uses to parse buffer 0 — emitting them is not
        // optional regardless of texture state. For textured nodes we skip
        // normals only from the Draco stream (CesiumJS generates them there).
        float[][] outPositions = new float[vertexCount][];
        float[][] outNormals = new float[vertexCount][3];
        float[][] outUVs = hasTexCoords ? new float[vertexCount][] : null;

        // ENU-to-ECEF rotation at the node's geographic center.
        // Note on normal consistency: per-polygon normals come from
        // PolygonTriangulator, which computes them in an ENU basis at the
        // polygon's own centroid latitude. Here we apply a single ENU-to-ECEF
        // rotation at the node's center latitude. For nodes spanning less than
        // a few kilometers the basis-rotation error between the two latitudes
        // is below the rendered-normal noise floor (<1e-4 rad). Larger nodes
        // would require per-triangle frame recomputation.
        double lonRad = Math.toRadians(centerX);
        double latRad = Math.toRadians(centerY);
        double sinLon = Math.sin(lonRad);
        double cosLon = Math.cos(lonRad);
        double sinLat = Math.sin(latRad);
        double cosLat = Math.cos(latRad);

        int idx = 0;
        for (int ti : validTriIndices) {
            int base = ti * 3;
            int[] tri = allTriangles.get(ti);
            for (int j = 0; j < 3; j++) {
                outPositions[idx] = weldedPositions[base + j];
                float[] n = mesh.getNormals().get(tri[j]);
                double ne = n[0], nn = n[1], nu = n[2];
                outNormals[idx][0] = (float) (-sinLon * ne - sinLat * cosLon * nn + cosLat * cosLon * nu);
                outNormals[idx][1] = (float) (cosLon * ne - sinLat * sinLon * nn + cosLat * sinLon * nu);
                outNormals[idx][2] = (float) (cosLat * nn + sinLat * nu);
                if (outUVs != null) {
                    outUVs[idx] = mesh.getTexCoords().get(tri[j]);
                }
                idx++;
            }
        }

        // Release source mesh — all needed data is now in output arrays
        node.setMesh(null);

        int[] vertexFeatureIndices = weld.computeFeatureIndices();

        int numTriangles = validTriIndices.size();

        // Dual-buffer layout matching Esri's NYC reference:
        //   geometries/0 — uncompressed (position + normal + [uv0] + color +
        //     per-feature featureId/faceRange), required by ArcGIS Pro for
        //     single-feature identify/picking.
        //   geometries/1 — Draco-compressed; used by CesiumJS and for
        //     ArcGIS rendering.
        List<Long> rangeFeatureIds = weld.rangeFeatureIds();
        List<int[]> faceRanges = weld.faceRanges();
        writeUncompressedGeometry(layerDir, node, outPositions, outNormals, outUVs,
                rangeFeatureIds, faceRanges);
        // Textured nodes skip normals in Draco (CesiumJS regenerates them);
        // untextured nodes include normals in Draco.
        float[][] dracoNormals = hasTexCoords ? null : outNormals;
        writeDracoGeometry(layerDir, node, centerY, outPositions, dracoNormals,
                outUVs, vertexFeatureIndices, numTriangles, rangeFeatureIds);
        return rangeFeatureIds;
    }

    /**
     * Write the uncompressed geometry buffer ({@code geometries/0}) in the
     * I3S SoA layout mirroring Esri's NYC reference. All four vertex
     * attributes are always emitted so the binary matches the global
     * {@code defaultGeometrySchema} (position + normal + uv0 + color) that
     * ArcGIS Pro uses to parse buffer 0:
     * <pre>
     *   Header (8 bytes): vertexCount UInt32 LE, featureCount UInt32 LE
     *   Per-vertex:
     *     position  Float32 × 3 × vertexCount
     *     normal    Float32 × 3 × vertexCount   (ECEF unit normals)
     *     uv0       Float32 × 2 × vertexCount   (zero-filled if untextured)
     *     color     UInt8   × 4 × vertexCount   (opaque white placeholder)
     *   Per-feature:
     *     featureId UInt64 × featureCount
     *     faceRange UInt32 × 2 × featureCount   (inclusive [startTri,endTri])
     * </pre>
     * Positions are stored as Float32 offsets from the node center:
     * {@code (lonDeg, latDeg, meters)} — same local frame as the welded
     * vertices produced by {@link VertexWelder}.
     */
    private static void writeUncompressedGeometry(Path layerDir, SceneNode node,
                                                  float[][] positions, float[][] normals,
                                                  float[][] uvs,
                                                  List<Long> rangeFeatureIds,
                                                  List<int[]> faceRanges) throws IOException {
        int vertexCount = positions.length;
        int featureCount = rangeFeatureIds.size();
        boolean hasUV = uvs != null;

        int posBytes = vertexCount * 12;
        int normBytes = vertexCount * 12;
        int uvBytes = vertexCount * 8;
        int colorBytes = vertexCount * 4;
        int fidBytes = featureCount * 8;
        int frBytes = featureCount * 8;
        int total = 8 + posBytes + normBytes + uvBytes + colorBytes + fidBytes + frBytes;

        ByteBuffer buf = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(vertexCount);
        buf.putInt(featureCount);
        for (float[] p : positions) {
            buf.putFloat(p[0]);
            buf.putFloat(p[1]);
            buf.putFloat(p[2]);
        }
        for (float[] n : normals) {
            buf.putFloat(n[0]);
            buf.putFloat(n[1]);
            buf.putFloat(n[2]);
        }
        for (int i = 0; i < vertexCount; i++) {
            if (hasUV) {
                buf.putFloat(uvs[i][0]);
                buf.putFloat(uvs[i][1]);
            } else {
                buf.putFloat(0f);
                buf.putFloat(0f);
            }
        }
        for (int i = 0; i < vertexCount; i++) {
            buf.put((byte) 0xFF);
            buf.put((byte) 0xFF);
            buf.put((byte) 0xFF);
            buf.put((byte) 0xFF);
        }
        for (long id : rangeFeatureIds) {
            buf.putLong(id);
        }
        for (int[] range : faceRanges) {
            buf.putInt(range[0]);
            buf.putInt(range[1]);
        }

        Path geometryDir = layerDir.resolve("nodes").resolve(String.valueOf(node.getIndex()))
                .resolve("geometries");
        Files.createDirectories(geometryDir);
        Files.write(geometryDir.resolve("0"), buf.array());
    }

    /**
     * Encode geometry using Draco compression. Attribute order matches the
     * compressedAttributes declaration in geometryDefinitions:
     * <ul>
     *   <li>Untextured: {@code position, normal, feature-index}</li>
     *   <li>Textured:   {@code position, uv0, feature-index} — normals are
     *       generated at runtime by CesiumJS from geometry</li>
     * </ul>
     */
    private static void writeDracoGeometry(Path layerDir, SceneNode node,
                                    double centerLatDeg,
                                    float[][] positions, float[][] normals,
                                    float[][] uvs,
                                    int[] featureIndices, int numTriangles,
                                    List<Long> rangeFeatureIds) throws IOException {
        int numVertices = positions.length;
        boolean hasUV = uvs != null;

        // Draco uses uniform quantization for POSITION — the largest axis range
        // determines the grid.  Scale X/Y to meters so all axes share a comparable range.
        double scaleX = 111_320.0 * Math.cos(Math.toRadians(centerLatDeg));
        double scaleY = 111_320.0;

        DracoMesh dracoMesh = new DracoMesh();
        dracoMesh.setNumPoints(numVertices);
        dracoMesh.setNumFaces(numTriangles);

        // Position attribute — scale X/Y to meters, null out source entries
        Vector3[] posVectors = new Vector3[numVertices];
        for (int i = 0; i < numVertices; i++) {
            posVectors[i] = new Vector3(
                    positions[i][0] * (float) scaleX,
                    positions[i][1] * (float) scaleY,
                    positions[i][2]);
            positions[i] = null;
        }
        dracoMesh.addAttribute(PointAttribute.wrap(AttributeType.POSITION, posVectors));
        posVectors = null;

        // Include normal in Draco only for untextured nodes.
        // For textured nodes, CesiumJS generates normals from geometry (matching SF reference).
        if (!hasUV) {
            Vector3[] normVectors = new Vector3[numVertices];
            for (int i = 0; i < numVertices; i++) {
                normVectors[i] = new Vector3(normals[i][0], normals[i][1], normals[i][2]);
            }
            dracoMesh.addAttribute(PointAttribute.wrap(AttributeType.NORMAL, normVectors));
        }

        // UV attribute (TEX_COORD) — CesiumJS auto-maps TEX_COORD → "uv0s"
        if (hasUV) {
            Vector2[] uvVectors = new Vector2[numVertices];
            for (int i = 0; i < numVertices; i++) {
                uvVectors[i] = new Vector2(uvs[i][0], uvs[i][1]);
                uvs[i] = null;
            }
            dracoMesh.addAttribute(PointAttribute.wrap(AttributeType.TEX_COORD, uvVectors));
            uvVectors = null;
        }

        // Feature-index attribute (GENERIC, INT32, 1 component per vertex).
        // Set uniqueId explicitly so we can reference it in injected metadata.
        // The uniqueId must match the attribute's position in the Draco file.
        DataBuffer intBuffer = new DataBuffer();
        intBuffer.setCapacity(numVertices * 4);
        for (int i = 0; i < numVertices; i++) {
            intBuffer.write(i * 4, featureIndices[i]);
        }
        PointAttribute featureAttr = new PointAttribute(
                AttributeType.GENERIC, DataType.INT32, 1, false, 4, 0, intBuffer);
        featureAttr.setNumUniqueEntries(numVertices);
        featureAttr.setUniqueId(FEATURE_INDEX_UID);
        dracoMesh.addAttribute(featureAttr);

        // Faces: triangle soup — sequential indices (0,1,2), (3,4,5), ...
        for (int t = 0; t < numTriangles; t++) {
            dracoMesh.addFace(new int[]{t * 3, t * 3 + 1, t * 3 + 2});
        }

        DracoEncodeOptions options = new DracoEncodeOptions();
        options.setPositionBits(14);
        if (!hasUV) {
            options.setNormalBits(10);
        }
        if (hasUV) {
            options.setTextureCoordinateBits(12);
        }
        options.setCompressionLevel(DracoCompressionLevel.STANDARD);

        try {
            byte[] compressed = Draco.encode(dracoMesh, options);
            compressed = injectDracoMetadata(compressed, 1.0 / scaleX, 1.0 / scaleY,
                    FEATURE_INDEX_UID, rangeFeatureIds);
            Path geometryDir = layerDir.resolve("nodes").resolve(String.valueOf(node.getIndex()))
                    .resolve("geometries");
            Files.createDirectories(geometryDir);
            Files.write(geometryDir.resolve("1"), compressed);
        } catch (DrakoException e) {
            throw new IOException("Draco encoding failed for node " + node.getIndex(), e);
        }
    }

    // ---- Draco metadata injection ----------------------------------------

    /**
     * Inject metadata into an encoded Draco binary.  The Drako Java library
     * does not support metadata encoding, so we patch the binary directly.
     * <p>
     * Metadata injected (all per-attribute):
     * <ul>
     *   <li><b>POSITION attribute</b>: {@code "i3s-scale_x"} and {@code "i3s-scale_y"}
     *       so CesiumJS can convert positions back from meters to degrees.</li>
     *   <li><b>GENERIC attribute</b>:
     *     <ul>
     *       <li>{@code "i3s-feature-ids"}: Int32 LE array of per-feature ids
     *           (the {@code OID} field values) in node-attribute order.
     *           Verified necessary for ArcGIS Pro single-feature picking —
     *           removing it causes picking to fall back to whole-node
     *           highlight even with the uncompressed buffer 0
     *           featureId/faceRange table present.</li>
     *       <li>{@code "i3s-attribute-type": "feature-index"} — attribute
     *           semantic hint used by CesiumJS.</li>
     *     </ul>
     *   </li>
     * </ul>
     */
    private static byte[] injectDracoMetadata(byte[] dracoData,
                                              double invScaleX, double invScaleY,
                                              short featureIndexUniqueId,
                                              List<Long> rangeFeatureIds) {
        byte[] metadataSection = buildMetadataSection(invScaleX, invScaleY,
                featureIndexUniqueId, rangeFeatureIds);

        // Set the metadata flag in the header (flags field is at offset 9-10, LE)
        short flags = (short) (((dracoData[10] & 0xFF) << 8) | (dracoData[9] & 0xFF));
        flags |= DRACO_METADATA_FLAG;
        dracoData[9] = (byte) (flags & 0xFF);
        dracoData[10] = (byte) ((flags >>> 8) & 0xFF);

        // Insert metadata section between header and encoder data
        byte[] result = new byte[dracoData.length + metadataSection.length];
        System.arraycopy(dracoData, 0, result, 0, DRACO_HEADER_SIZE);
        System.arraycopy(metadataSection, 0, result, DRACO_HEADER_SIZE, metadataSection.length);
        System.arraycopy(dracoData, DRACO_HEADER_SIZE, result,
                DRACO_HEADER_SIZE + metadataSection.length,
                dracoData.length - DRACO_HEADER_SIZE);
        return result;
    }

    /**
     * Build the Draco metadata section bytes.  Format (matches the C++ Draco
     * MetadataEncoder and the Drako MetadataDecoder):
     * <pre>
     *   varint  num_attribute_metadata
     *   for each attribute metadata:
     *     varint  att_unique_id
     *     metadata_block
     *   metadata_block  (geometry-level)
     *
     *   metadata_block:
     *     varint  num_entries
     *     for each entry:
     *       varint  key_length  +  bytes  key
     *       varint  value_length  +  bytes  value
     *     varint  num_sub_metadata
     * </pre>
     */
    private static byte[] buildMetadataSection(double invScaleX, double invScaleY,
                                                short featureIndexUniqueId,
                                                List<Long> rangeFeatureIds) {
        byte[] attrTypeKey = "i3s-attribute-type".getBytes(StandardCharsets.UTF_8);
        byte[] attrTypeVal = "feature-index".getBytes(StandardCharsets.UTF_8);
        byte[] scaleXKey = "i3s-scale_x".getBytes(StandardCharsets.UTF_8);
        byte[] scaleYKey = "i3s-scale_y".getBytes(StandardCharsets.UTF_8);
        byte[] scaleXVal = doubleToLeBytes(invScaleX);
        byte[] scaleYVal = doubleToLeBytes(invScaleY);
        byte[] featureIdsKey = "i3s-feature-ids".getBytes(StandardCharsets.UTF_8);
        byte[] featureIdsVal = buildFeatureIdsValue(rangeFeatureIds);
        byte[] featureIdsValLen = encodeVarint(featureIdsVal.length);

        // Header overhead fixed; feature-ids blob dominates size — allocate tight
        ByteBuffer buf = ByteBuffer.allocate(256 + featureIdsVal.length + featureIdsValLen.length);

        // --- 2 attribute metadata entries ---
        buf.put(encodeVarint(2));

        // Attribute 0 (POSITION, unique_id=0): scale factors
        buf.put(encodeVarint(0));
        buf.put(encodeVarint(2)); // 2 entries
        buf.put(encodeVarint(scaleXKey.length));
        buf.put(scaleXKey);
        buf.put(encodeVarint(scaleXVal.length));
        buf.put(scaleXVal);
        buf.put(encodeVarint(scaleYKey.length));
        buf.put(scaleYKey);
        buf.put(encodeVarint(scaleYVal.length));
        buf.put(scaleYVal);
        buf.put(encodeVarint(0)); // 0 sub-metadata

        // Attribute N (GENERIC): feature-ids table + attribute-type tag
        buf.put(encodeVarint(featureIndexUniqueId));
        buf.put(encodeVarint(2)); // 2 entries
        buf.put(encodeVarint(featureIdsKey.length));
        buf.put(featureIdsKey);
        buf.put(featureIdsValLen);
        buf.put(featureIdsVal);
        buf.put(encodeVarint(attrTypeKey.length));
        buf.put(attrTypeKey);
        buf.put(encodeVarint(attrTypeVal.length));
        buf.put(attrTypeVal);
        buf.put(encodeVarint(0)); // 0 sub-metadata

        // --- geometry-level metadata: empty ---
        buf.put(encodeVarint(0)); // 0 entries
        buf.put(encodeVarint(0)); // 0 sub-metadata

        return Arrays.copyOf(buf.array(), buf.position());
    }

    /**
     * Serialize per-feature ids as a packed Int32 LE array, matching Esri's
     * {@code i3s-feature-ids} Draco metadata layout. Values must match the
     * integer OID stored in the node's {@code OID} attribute buffer (via
     * {@link org.citydb.vis.model.FeatureData#id()}) so ArcGIS's pick
     * resolution stays consistent with the OID field.
     */
    private static byte[] buildFeatureIdsValue(List<Long> rangeFeatureIds) {
        ByteBuffer out = ByteBuffer.allocate(rangeFeatureIds.size() * 4)
                .order(ByteOrder.LITTLE_ENDIAN);
        for (long id : rangeFeatureIds) {
            out.putInt((int) id);
        }
        return out.array();
    }

    private static byte[] doubleToLeBytes(double value) {
        byte[] bytes = new byte[8];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putDouble(value);
        return bytes;
    }

    /** Encode a non-negative int as a Draco varint (LEB128). */
    private static byte[] encodeVarint(int value) {
        if (value < 0x80) {
            return new byte[]{(byte) value};
        }
        byte[] tmp = new byte[5];
        int pos = 0;
        int v = value;
        while (v >= 0x80) {
            tmp[pos++] = (byte) ((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        tmp[pos++] = (byte) v;
        return Arrays.copyOf(tmp, pos);
    }

}
