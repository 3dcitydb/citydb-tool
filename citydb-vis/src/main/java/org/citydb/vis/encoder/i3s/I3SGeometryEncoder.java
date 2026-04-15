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
import org.citydb.vis.util.BufferUtils;
import org.citydb.vis.util.GeoTransform;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Encodes I3S node meshes into a single Draco-compressed geometry buffer
 * ({@code geometries/0}). Attributes for untextured nodes are
 * {@code position, normal, feature-index}; textured nodes use
 * {@code position, uv0, feature-index} with normals regenerated at render
 * time by the client.
 */
public class I3SGeometryEncoder {
    /**
     * Unique ID for the feature-index Draco attribute. Set explicitly via
     * {@link PointAttribute#setUniqueId} so the attribute can be referenced
     * unambiguously in the injected per-attribute metadata section
     * (independent of the attribute's position in the Draco stream).
     */
    private static final short FEATURE_INDEX_UID = 2;
    /** Draco header size: "DRACO" (5) + major (1) + minor (1) + type (1) + method (1) + flags (2) */
    private static final int DRACO_HEADER_SIZE = 11;
    /** Bit in the Draco header flags field that indicates metadata is present. */
    private static final short DRACO_METADATA_FLAG = (short) 0x8000;

    /**
     * Encode and write a node's geometry to {@code geometries/0} as a single
     * Draco-compressed buffer.
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

        if (!hasTexCoords) {
            node.setTextured(false);
        }

        BoundingVolume mbs = node.getBoundingVolume();
        double centerX = mbs.getCenterX();
        double centerY = mbs.getCenterY();
        double centerZ = mbs.getCenterZ();

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

        // Collect positions, ECEF normals (untextured only), and UVs (textured
        // only) into arrays for Draco encoding. Textured nodes skip normals —
        // the client regenerates them from geometry.
        float[][] outPositions = new float[vertexCount][];
        float[][] outNormals = hasTexCoords ? null : new float[vertexCount][3];
        float[][] outUVs = hasTexCoords ? new float[vertexCount][] : null;

        // ENU-to-ECEF rotation at the node's geographic center.
        // Note on normal consistency: per-polygon normals come from
        // PolygonTriangulator, which computes them in an ENU basis at the
        // polygon's own centroid latitude. Here we apply a single ENU-to-ECEF
        // rotation at the node's center latitude. For nodes spanning less than
        // a few kilometers the basis-rotation error between the two latitudes
        // is below the rendered-normal noise floor (<1e-4 rad). Larger nodes
        // would require per-triangle frame recomputation.
        double sinLon = 0, cosLon = 0, sinLat = 0, cosLat = 0;
        if (outNormals != null) {
            double lonRad = Math.toRadians(centerX);
            double latRad = Math.toRadians(centerY);
            sinLon = Math.sin(lonRad);
            cosLon = Math.cos(lonRad);
            sinLat = Math.sin(latRad);
            cosLat = Math.cos(latRad);
        }

        int idx = 0;
        for (int ti : validTriIndices) {
            int base = ti * 3;
            int[] tri = allTriangles.get(ti);
            for (int j = 0; j < 3; j++) {
                outPositions[idx] = weldedPositions[base + j];
                if (outNormals != null) {
                    float[] n = mesh.getNormals().get(tri[j]);
                    double ne = n[0], nn = n[1], nu = n[2];
                    outNormals[idx][0] = (float) (-sinLon * ne - sinLat * cosLon * nn + cosLat * cosLon * nu);
                    outNormals[idx][1] = (float) (cosLon * ne - sinLat * sinLon * nn + cosLat * sinLon * nu);
                    outNormals[idx][2] = (float) (cosLat * nn + sinLat * nu);
                }
                if (outUVs != null) {
                    outUVs[idx] = mesh.getTexCoords().get(tri[j]);
                }
                idx++;
            }
        }

        node.setMesh(null);

        int[] vertexFeatureIndices = weld.computeFeatureIndices();
        int numTriangles = validTriIndices.size();
        List<Long> rangeFeatureIds = weld.rangeFeatureIds();

        writeDracoGeometry(layerDir, node, centerY, outPositions, outNormals,
                outUVs, vertexFeatureIndices, numTriangles, rangeFeatureIds);
        return rangeFeatureIds;
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
        double scaleX = GeoTransform.metersPerDegreeLon(centerLatDeg);
        double scaleY = GeoTransform.WGS84_METERS_PER_DEGREE_LAT;

        DracoMesh dracoMesh = new DracoMesh();
        dracoMesh.setNumPoints(numVertices);
        dracoMesh.setNumFaces(numTriangles);

        // Position attribute — scale X/Y to meters
        Vector3[] posVectors = new Vector3[numVertices];
        for (int i = 0; i < numVertices; i++) {
            posVectors[i] = new Vector3(
                    positions[i][0] * (float) scaleX,
                    positions[i][1] * (float) scaleY,
                    positions[i][2]);
        }
        dracoMesh.addAttribute(PointAttribute.wrap(AttributeType.POSITION, posVectors));

        // Include normal in Draco only for untextured nodes.
        // For textured nodes, the client regenerates normals from geometry.
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
            }
            dracoMesh.addAttribute(PointAttribute.wrap(AttributeType.TEX_COORD, uvVectors));
        }

        // Feature-index attribute (GENERIC, INT32, 1 component per vertex).
        // Set uniqueId explicitly so we can reference it in injected metadata
        // independently of the attribute's position in the Draco stream.
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
                    rangeFeatureIds);
            Path geometryDir = layerDir.resolve("nodes").resolve(String.valueOf(node.getIndex()))
                    .resolve("geometries");
            Files.createDirectories(geometryDir);
            Files.write(geometryDir.resolve("0"), compressed);
        } catch (DrakoException e) {
            throw new IOException("Draco encoding failed for node " + node.getIndex(), e);
        }
    }

    // ---- Draco metadata injection ----------------------------------------

    /**
     * Inject metadata into an encoded Draco binary. The Drako Java library
     * does not support metadata encoding, so we patch the binary directly.
     * <p>
     * Metadata injected (all per-attribute):
     * <ul>
     *   <li><b>POSITION attribute</b>: {@code "i3s-scale_x"} and {@code "i3s-scale_y"}
     *       so the client can convert positions back from meters to degrees.</li>
     *   <li><b>GENERIC feature-index attribute</b>:
     *     <ul>
     *       <li>{@code "i3s-feature-ids"}: Int32 LE array of per-feature ids
     *           (OID field values) in node-attribute order — required for
     *           ArcGIS Pro single-feature picking.</li>
     *       <li>{@code "i3s-attribute-type": "feature-index"} — attribute
     *           semantic hint.</li>
     *     </ul>
     *   </li>
     * </ul>
     */
    private static byte[] injectDracoMetadata(byte[] dracoData,
                                              double invScaleX, double invScaleY,
                                              List<Long> rangeFeatureIds) {
        byte[] metadataSection = buildMetadataSection(invScaleX, invScaleY, rangeFeatureIds);

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
        ByteBuffer buf = BufferUtils.allocateLittleEndian(256 + featureIdsVal.length + featureIdsValLen.length);

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
        buf.put(encodeVarint(FEATURE_INDEX_UID));
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
        ByteBuffer out = BufferUtils.allocateLittleEndian(rangeFeatureIds.size() * 4);
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
