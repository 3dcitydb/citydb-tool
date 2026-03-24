/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.writer;

import com.openize.drako.*;
import org.citydb.vis.geometry.TriangleMesh;
import org.citydb.vis.scene.BoundingVolume;
import org.citydb.vis.scene.I3SNode;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Encodes I3S node meshes into binary geometry buffers.
 * Handles vertex welding, degenerate triangle removal, and ENU-to-ECEF normal transformation.
 * Produces both raw (buffer 0) and Draco-compressed (buffer 1) geometry.
 */
class I3SGeometryEncoder {
    /** Unique ID assigned to the feature-index Draco attribute, used for metadata injection. */
    private static final short FEATURE_INDEX_UNIQUE_ID = 2;

    void writeNodeGeometry(Path layerDir, I3SNode node) throws IOException {
        TriangleMesh mesh = node.getMesh();

        BoundingVolume mbs = node.getMbs();
        double centerX = mbs != null ? mbs.getCenterX() : 0;
        double centerY = mbs != null ? mbs.getCenterY() : 0;
        double centerZ = mbs != null ? mbs.getCenterZ() : 0;

        // Weld vertex positions using spatial hash grid — O(N) instead of O(N²)
        float[][] weldedPositions = weldVertexPositions(mesh, centerX, centerY, centerZ);

        // Filter out degenerate triangles (welding may collapse vertices)
        List<int[]> allTriangles = mesh.getTriangles();
        List<Long> triFeatureIds = mesh.getFeatureIds();
        List<Integer> validTriIndices = new ArrayList<>();
        int vi = 0;
        for (int t = 0; t < allTriangles.size(); t++) {
            float[] p0 = weldedPositions[vi], p1 = weldedPositions[vi + 1], p2 = weldedPositions[vi + 2];
            if (!positionsEqual(p0, p1) && !positionsEqual(p0, p2) && !positionsEqual(p1, p2)) {
                validTriIndices.add(t);
            }
            vi += 3;
        }

        int vertexCount = validTriIndices.size() * 3;
        node.setOutputVertexCount(vertexCount);

        // ENU-to-ECEF rotation at the node's geographic center
        double lonRad = Math.toRadians(centerX);
        double latRad = Math.toRadians(centerY);
        double sinLon = Math.sin(lonRad), cosLon = Math.cos(lonRad);
        double sinLat = Math.sin(latRad), cosLat = Math.cos(latRad);

        // Compute face ranges from valid triangles
        List<int[]> faceRanges = new ArrayList<>();
        if (!validTriIndices.isEmpty()) {
            int start = 0;
            long currentId = triFeatureIds.get(validTriIndices.get(0));
            for (int i = 1; i < validTriIndices.size(); i++) {
                long id = triFeatureIds.get(validTriIndices.get(i));
                if (id != currentId) {
                    faceRanges.add(new int[]{start, i - 1});
                    start = i;
                    currentId = id;
                }
            }
            faceRanges.add(new int[]{start, validTriIndices.size() - 1});
        }

        // Collect positions and ECEF normals into arrays (shared by raw and Draco)
        float[][] outPositions = new float[vertexCount][];
        float[][] outNormals = new float[vertexCount][3];
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
                idx++;
            }
        }

        // Release source mesh — all needed data is now in outPositions/outNormals
        node.setMesh(null);

        // Compute per-vertex feature index (for Draco)
        int[] vertexFeatureIndices = new int[vertexCount];
        for (int f = 0; f < faceRanges.size(); f++) {
            int[] range = faceRanges.get(f);
            for (int t = range[0]; t <= range[1]; t++) {
                vertexFeatureIndices[t * 3] = f;
                vertexFeatureIndices[t * 3 + 1] = f;
                vertexFeatureIndices[t * 3 + 2] = f;
            }
        }

        int numTriangles = validTriIndices.size();
        // Release intermediate references no longer needed
        weldedPositions = null;
        allTriangles = null;
        triFeatureIds = null;
        validTriIndices = null;

        // Write raw geometry to geometries/1 (backup, not declared) — must run
        // before Draco because writeDracoGeometry nulls array entries.
        writeRawGeometry(layerDir, node, outPositions, outNormals, faceRanges);

        // Write Draco geometry to geometries/0 (declared in geometryDefinitions).
        writeDracoGeometry(layerDir, node, centerY, outPositions, outNormals,
                vertexFeatureIndices, numTriangles);
        outPositions = null;
        outNormals = null;
    }

    private void writeRawGeometry(Path layerDir, I3SNode node,
                                  float[][] positions, float[][] normals,
                                  List<int[]> faceRanges) throws IOException {
        int vertexCount = positions.length;
        int bufferSize = 8 + vertexCount * 12 + vertexCount * 12
                + faceRanges.size() * 8 + faceRanges.size() * 8;
        ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        buffer.putInt(vertexCount);
        buffer.putInt(faceRanges.size());

        for (float[] pos : positions) {
            buffer.putFloat(pos[0]);
            buffer.putFloat(pos[1]);
            buffer.putFloat(pos[2]);
        }

        for (float[] n : normals) {
            buffer.putFloat(n[0]);
            buffer.putFloat(n[1]);
            buffer.putFloat(n[2]);
        }

        // Feature IDs: UInt64 per feature (0-based sequential index)
        for (int i = 0; i < faceRanges.size(); i++) {
            buffer.putLong(i);
        }

        // Face ranges: [startFaceIndex, endFaceIndex] per feature (UInt32 x 2)
        for (int[] range : faceRanges) {
            buffer.putInt(range[0]);
            buffer.putInt(range[1]);
        }

        Files.write(layerDir.resolve("nodes").resolve(String.valueOf(node.getIndex()))
                .resolve("geometries").resolve("1"), buffer.array());
    }

    /**
     * Encode geometry using Draco compression and write to geometries/1.
     * Attributes are added in this order to match the compressedAttributes
     * declaration in geometryDefinitions: position, normal, feature-index.
     */
    private void writeDracoGeometry(Path layerDir, I3SNode node,
                                    double centerLatDeg,
                                    float[][] positions, float[][] normals,
                                    int[] featureIndices, int numTriangles) throws IOException {
        int numVertices = positions.length;

        // Draco uses uniform quantization for POSITION — the largest axis range
        // determines the grid.  Our positions are Float32 offsets where X/Y are in
        // degrees (~0.001) and Z is in meters (~300).  Without scaling, X/Y collapse.
        // Scale X/Y to meters so all axes share a comparable range.
        // CesiumJS will multiply decoded positions by i3s-scale_x/y to convert back.
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

        // Normal attribute — same pattern
        Vector3[] normVectors = new Vector3[numVertices];
        for (int i = 0; i < numVertices; i++) {
            normVectors[i] = new Vector3(normals[i][0], normals[i][1], normals[i][2]);
            normals[i] = null;
        }
        dracoMesh.addAttribute(PointAttribute.wrap(AttributeType.NORMAL, normVectors));
        normVectors = null;

        // Feature-index attribute (GENERIC, INT32, 1 component per vertex).
        // Set uniqueId explicitly so we can reference it in injected metadata.
        DataBuffer intBuffer = new DataBuffer();
        intBuffer.setCapacity(numVertices * 4);
        for (int i = 0; i < numVertices; i++) {
            intBuffer.write(i * 4, featureIndices[i]);
        }
        PointAttribute featureAttr = new PointAttribute(
                AttributeType.GENERIC, DataType.INT32, 1, false, 4, 0, intBuffer);
        featureAttr.setNumUniqueEntries(numVertices);
        featureAttr.setUniqueId(FEATURE_INDEX_UNIQUE_ID);
        dracoMesh.addAttribute(featureAttr);

        // Faces: triangle soup — sequential indices (0,1,2), (3,4,5), ...
        for (int t = 0; t < numTriangles; t++) {
            dracoMesh.addFace(new int[]{t * 3, t * 3 + 1, t * 3 + 2});
        }

        DracoEncodeOptions options = new DracoEncodeOptions();
        options.setPositionBits(14);
        options.setNormalBits(10);
        options.setCompressionLevel(DracoCompressionLevel.STANDARD);

        try {
            byte[] compressed = Draco.encode(dracoMesh, options);
            // The Drako library does not encode per-attribute metadata.
            // CesiumJS requires "i3s-attribute-type": "feature-index" metadata
            // on the GENERIC attribute to identify it for feature picking.
            // Inject the metadata section into the encoded Draco binary.
            compressed = injectDracoMetadata(compressed, 1.0 / scaleX, 1.0 / scaleY);
            Files.write(layerDir.resolve("nodes").resolve(String.valueOf(node.getIndex()))
                    .resolve("geometries").resolve("0"), compressed);
        } catch (DrakoException e) {
            throw new IOException("Draco encoding failed for node " + node.getIndex(), e);
        }
    }

    // ---- Draco metadata injection ----------------------------------------

    /** Draco header size: "DRACO" (5) + major (1) + minor (1) + type (1) + method (1) + flags (2) */
    private static final int DRACO_HEADER_SIZE = 11;
    /** Bit in the Draco header flags field that indicates metadata is present. */
    private static final short DRACO_METADATA_FLAG = (short) 0x8000;

    /**
     * Inject metadata into an encoded Draco binary.  The Drako Java library
     * does not support metadata encoding, so we patch the binary directly.
     * <p>
     * Metadata injected (all per-attribute, as required by CesiumJS):
     * <ul>
     *   <li><b>POSITION attribute</b>: {@code "i3s-scale_x"} and {@code "i3s-scale_y"}
     *       so CesiumJS can convert positions back from meters to degrees.</li>
     *   <li><b>GENERIC attribute</b>: {@code "i3s-attribute-type": "feature-index"}
     *       so CesiumJS can identify it for feature picking.</li>
     * </ul>
     */
    private static byte[] injectDracoMetadata(byte[] dracoData,
                                              double invScaleX, double invScaleY) {
        byte[] metadataSection = buildMetadataSection(invScaleX, invScaleY);

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
    private static byte[] buildMetadataSection(double invScaleX, double invScaleY) {
        byte[] attrTypeKey = "i3s-attribute-type".getBytes(StandardCharsets.UTF_8);
        byte[] attrTypeVal = "feature-index".getBytes(StandardCharsets.UTF_8);
        byte[] scaleXKey = "i3s-scale_x".getBytes(StandardCharsets.UTF_8);
        byte[] scaleYKey = "i3s-scale_y".getBytes(StandardCharsets.UTF_8);
        byte[] scaleXVal = doubleToLeBytes(invScaleX);
        byte[] scaleYVal = doubleToLeBytes(invScaleY);

        // Use a generous buffer; trim at the end
        ByteBuffer buf = ByteBuffer.allocate(256);

        // --- 2 attribute metadata entries ---
        // CesiumJS reads i3s-scale_x/y and i3s-attribute-type from
        // per-attribute metadata (GetAttributeMetadata), not geometry-level.
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

        // Attribute 2 (GENERIC, unique_id=2): feature-index type
        buf.put(encodeVarint(FEATURE_INDEX_UNIQUE_ID));
        buf.put(encodeVarint(1)); // 1 entry
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
        // For values ≥ 128, encode multi-byte varint
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

    // ---- Vertex welding --------------------------------------------------

    /**
     * Weld vertex positions: find vertices within 2cm of each other and map
     * them to the same Float32 position. Uses a spatial hash grid for O(N)
     * performance instead of O(N²) brute force.
     */
    private float[][] weldVertexPositions(TriangleMesh mesh, double centerX,
                                          double centerY, double centerZ) {
        double scaleX = 111_320.0 * Math.cos(Math.toRadians(centerY));
        double scaleY = 111_320.0;
        float weldTolerance = 0.02f; // 2cm
        float weldTolerance2 = weldTolerance * weldTolerance;

        List<double[]> positions = mesh.getPositions();
        int posCount = positions.size();

        float[][] f32 = new float[posCount][3];
        float[][] mPos = new float[posCount][3];
        for (int i = 0; i < posCount; i++) {
            double[] p = positions.get(i);
            f32[i][0] = (float) (p[0] - centerX);
            f32[i][1] = (float) (p[1] - centerY);
            f32[i][2] = (float) (p[2] - centerZ);
            mPos[i][0] = (float) ((p[0] - centerX) * scaleX);
            mPos[i][1] = (float) ((p[1] - centerY) * scaleY);
            mPos[i][2] = f32[i][2];
        }

        // Spatial hash grid: cell size = weld tolerance
        float cellSize = weldTolerance;
        Map<Long, List<Integer>> grid = new HashMap<>();
        int[] remap = new int[posCount];

        for (int i = 0; i < posCount; i++) {
            int gx = Math.round(mPos[i][0] / cellSize);
            int gy = Math.round(mPos[i][1] / cellSize);
            int gz = Math.round(mPos[i][2] / cellSize);

            remap[i] = i;
            // Search 3x3x3 neighborhood for a canonical vertex within tolerance
            outer:
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        long key = gridKey(gx + dx, gy + dy, gz + dz);
                        List<Integer> cell = grid.get(key);
                        if (cell == null) continue;
                        for (int j : cell) {
                            float ex = mPos[i][0] - mPos[j][0];
                            float ey = mPos[i][1] - mPos[j][1];
                            float ez = mPos[i][2] - mPos[j][2];
                            if (ex * ex + ey * ey + ez * ez < weldTolerance2) {
                                remap[i] = j;
                                break outer;
                            }
                        }
                    }
                }
            }

            // If this vertex is canonical (not remapped), add it to the grid
            if (remap[i] == i) {
                long key = gridKey(gx, gy, gz);
                grid.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
            }
        }

        int triCount = mesh.getTriangleCount();
        float[][] result = new float[triCount * 3][];
        int vi2 = 0;
        for (int[] tri : mesh.getTriangles()) {
            for (int idx2 : tri) {
                result[vi2++] = f32[remap[idx2]];
            }
        }
        return result;
    }

    private static long gridKey(int x, int y, int z) {
        return ((long) x * 73856093L) ^ ((long) y * 19349669L) ^ ((long) z * 83492791L);
    }

    private static boolean positionsEqual(float[] a, float[] b) {
        return a[0] == b[0] && a[1] == b[1] && a[2] == b[2];
    }
}
