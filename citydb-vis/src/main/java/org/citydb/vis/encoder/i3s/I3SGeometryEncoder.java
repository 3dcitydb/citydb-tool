/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Encodes I3S node meshes into a single Draco-compressed geometry buffer
 * ({@code geometries/0}). Attribute layout per node category:
 * <ul>
 *   <li>untextured (no appearance): {@code position, normal, feature-index}</li>
 *   <li>textured:                   {@code position, uv0, feature-index}</li>
 *   <li>textured-colored:           {@code position, uv0, color, feature-index}</li>
 *   <li>colored (X3DMaterial only): {@code position, color, feature-index}</li>
 * </ul>
 * Policy: NORMAL is emitted only when the node has no appearance (no texture
 * and no X3DMaterial vertex colors). The plain path is always shaded
 * (PBR + per-face NORMAL); the user's configured default color (see
 * {@code DefaultObjectStyle}) rides on the material's {@code baseColorFactor}
 * separately. Any node with texture or X3DMaterial skips NORMAL so the
 * authored color/texture renders at full intensity — X3DMaterial in this
 * project is used for thematic / heat-map style visualisation where
 * Lambertian shading would dim the authored colors.
 */
public class I3SGeometryEncoder {
    /**
     * Unique ID for the feature-index Draco attribute, used to key the
     * injected metadata block to that attribute. Must not collide with the
     * positional uniqueIds Drako auto-assigns to POSITION/NORMAL/UV/COLOR
     * (0..N-1), so we pick a value well above any plausible attribute slot.
     */
    private static final short FEATURE_INDEX_UID = 100;
    /** Quantization bits per COLOR channel — 8 matches the browser's 8 bpc
     *  framebuffer; higher just bloats the Draco buffer for no visible gain. */
    private static final int COLOR_QUANT_BITS = 8;
    /** Default RGBA for vertices in a layer that carries colors but a given
     *  mesh has none — keeps COLOR_0 in lock-step with the layer's
     *  compressedAttributes declaration without tinting the texture sample. */
    private static final float[] WHITE_RGBA = new float[]{1f, 1f, 1f, 1f};
    /** Draco header size: "DRACO" (5) + major (1) + minor (1) + type (1) + method (1) + flags (2) */
    private static final int DRACO_HEADER_SIZE = 11;
    /** Bit in the Draco header flags field that indicates metadata is present. */
    private static final short DRACO_METADATA_FLAG = (short) 0x8000;

    /**
     * Result of encoding a node's geometry. {@code featureAabbs} is aligned
     * with {@code rangeFeatureIds} and contains the absolute-coordinate AABB
     * {@code [minX, minY, minZ, maxX, maxY, maxZ]} for each feature — the
     * ArcGIS Maps SDK for JavaScript needs distinct per-feature mbb values
     * to build the per-node pick BVH; a shared node-level bbox collapses
     * the BVH and makes picks intermittently miss under oblique views.
     */
    public record NodeGeometryResult(List<Long> rangeFeatureIds,
                                     List<double[]> featureAabbs) {
    }

    /**
     * Encode and write a node's geometry to {@code geometries/0} as a single
     * Draco-compressed buffer.
     *
     * @return ordered feature ids and per-feature AABBs if a geometry file
     *         was written, {@code null} if welding/degenerate filtering left
     *         the mesh empty (caller should treat this node as a non-mesh
     *         node). The caller must use this list to align per-node
     *         feature/attribute output with the Draco feature-index attribute.
     */
    public NodeGeometryResult writeNodeGeometry(Path layerDir, SceneNode node,
                                                boolean layerHasColors) throws IOException {
        TriangleMesh mesh = node.getMesh();
        boolean hasTexCoords = mesh.hasTexCoords();
        // Pure-colored path: no texture + baked X3DMaterial colors. Textured
        // nodes that also carry colors keep the textured material and let
        // glTF PBR modulate the texture sample via COLOR_0.
        boolean nodeIsColored = !hasTexCoords && mesh.hasColors();
        // The Draco buffer must mirror the layer's compressedAttributes
        // declaration exactly, otherwise CesiumJS drops the node. Textured
        // nodes in a layer with hasColors emit a default-white COLOR_0;
        // untextured nodes emit COLOR_0 only on the pure-colored path.
        boolean emitColor = layerHasColors && (hasTexCoords || nodeIsColored);

        if (!hasTexCoords) {
            node.setTextured(false);
        }
        node.setColored(nodeIsColored);

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

        // NORMAL only on the no-appearance path (no texture and no X3DMaterial).
        // See class Javadoc.
        boolean meshHasColors = mesh.hasColors();
        boolean emitNormals = !hasTexCoords && !meshHasColors;

        float[][] outPositions = new float[vertexCount][];
        float[][] outNormals = emitNormals ? new float[vertexCount][3] : null;
        float[][] outUVs = hasTexCoords ? new float[vertexCount][] : null;
        float[][] outColors = emitColor ? new float[vertexCount][] : null;

        // ENU-to-ECEF rotation at the node's geographic center.
        // Note on normal consistency: per-polygon normals come from
        // PolygonTriangulator, which computes them in an ENU basis at the
        // polygon's own centroid latitude. Here we apply a single ENU-to-ECEF
        // rotation at the node's center latitude. For nodes spanning less than
        // a few kilometers the basis-rotation error between the two latitudes
        // is below the rendered-normal noise floor (<1e-4 rad). Larger nodes
        // would require per-triangle frame recomputation.
        GeoTransform.EnuBasis enu = outNormals != null
                ? GeoTransform.EnuBasis.at(centerX, centerY) : null;

        boolean[] anyAlphaBelowOne = new boolean[1];
        VertexWelder.iterateOutputVertices(weld, mesh, (idx, weldedPos, srcIdx) -> {
            outPositions[idx] = weldedPos;
            if (outNormals != null) {
                enu.rotateNormalToEcef(mesh.getNormals().get(srcIdx), outNormals[idx]);
            }
            if (outUVs != null) {
                outUVs[idx] = mesh.getTexCoords().get(srcIdx);
            }
            if (outColors != null) {
                if (meshHasColors) {
                    float[] c = mesh.getColors().get(srcIdx);
                    outColors[idx] = c;
                    if (c[3] < 0.999f) {
                        anyAlphaBelowOne[0] = true;
                    }
                } else {
                    outColors[idx] = WHITE_RGBA;
                }
            }
        });

        // Textured material is layer-level OPAQUE — mixed-node colored polys
        // lose alpha by design (one-material-per-node I3S limitation); only
        // pure-colored nodes can opt into BLEND.
        if (nodeIsColored) {
            node.setColoredBlend(anyAlphaBelowOne[0]);
        }

        List<double[]> featureAabbs = computeFeatureAabbs(weld, centerX, centerY, centerZ);

        node.setMesh(null);

        int[] vertexFeatureIndices = weld.computeFeatureIndices();
        int numTriangles = weld.validTriIndices().size();
        List<Long> rangeFeatureIds = weld.rangeFeatureIds();

        writeDracoGeometry(layerDir, node, centerY, outPositions, outNormals,
                outUVs, outColors, vertexFeatureIndices, numTriangles, rangeFeatureIds);
        return new NodeGeometryResult(rangeFeatureIds, featureAabbs);
    }

    /**
     * Compute per-feature AABBs from welded (center-relative) positions,
     * converted back to absolute geographic coordinates. Returns a list
     * aligned with {@link VertexWelder.WeldResult#rangeFeatureIds()}.
     */
    private static List<double[]> computeFeatureAabbs(VertexWelder.WeldResult weld,
                                                       double centerX, double centerY,
                                                       double centerZ) {
        float[][] welded = weld.weldedPositions();
        List<Integer> validTri = weld.validTriIndices();
        List<int[]> faceRanges = weld.faceRanges();
        List<double[]> aabbs = new ArrayList<>(faceRanges.size());
        for (int[] range : faceRanges) {
            double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY,
                    minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY,
                    maxZ = Double.NEGATIVE_INFINITY;
            for (int i = range[0]; i <= range[1]; i++) {
                int base = validTri.get(i) * 3;
                for (int j = 0; j < 3; j++) {
                    float[] p = welded[base + j];
                    if (p[0] < minX) minX = p[0]; if (p[0] > maxX) maxX = p[0];
                    if (p[1] < minY) minY = p[1]; if (p[1] > maxY) maxY = p[1];
                    if (p[2] < minZ) minZ = p[2]; if (p[2] > maxZ) maxZ = p[2];
                }
            }
            aabbs.add(new double[]{
                    minX + centerX, minY + centerY, minZ + centerZ,
                    maxX + centerX, maxY + centerY, maxZ + centerZ});
        }
        return aabbs;
    }

    /**
     * Encode geometry using Draco compression. Attribute order matches the
     * compressedAttributes declaration in geometryDefinitions (see class
     * Javadoc). CesiumJS {@code decodeI3S.js} auto-maps Draco
     * {@code AttributeType.COLOR} to glTF {@code COLOR_0} (FLOAT VEC4), so
     * no metadata override is needed for the colored variant.
     */
    private static void writeDracoGeometry(Path layerDir, SceneNode node,
                                    double centerLatDeg,
                                    float[][] positions, float[][] normals,
                                    float[][] uvs, float[][] colors,
                                    int[] featureIndices, int numTriangles,
                                    List<Long> rangeFeatureIds) throws IOException {
        int numVertices = positions.length;
        boolean hasUV = uvs != null;
        boolean hasColor = colors != null;

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

        // NORMAL only on the no-appearance path. See class Javadoc.
        boolean hasNormal = normals != null;
        if (hasNormal) {
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

        // COLOR attribute. The peculiar FLOAT32 + pre-scaled-to-[0,255]
        // encoding is forced by two library quirks:
        //  - CesiumJS decodeI3S unconditionally divides decoded color values
        //    by 255, so the on-disk payload must already be in [0, 255];
        //  - Drako's integer attribute encoder loses every component past
        //    the first for multi-component attributes, so UINT8 RGBA decodes
        //    as (R, 0, 0, 0). Only the FLOAT32 quantization path is
        //    multi-component-correct.
        // FLOAT32 with values pre-scaled to [0, 255] plus setColorBits(8)
        // threads both: Cesium's /255 yields the original [0, 1] colors,
        // and Drako's 256-level quantizer round-trips integer-valued sources
        // exactly.
        if (hasColor) {
            DataBuffer colorBuffer = new DataBuffer();
            colorBuffer.setCapacity(numVertices * 16);
            for (int i = 0; i < numVertices; i++) {
                float[] c = colors[i];
                int base = i * 16;
                colorBuffer.write(base,      clampUnit(c[0]) * 255f);
                colorBuffer.write(base + 4,  clampUnit(c[1]) * 255f);
                colorBuffer.write(base + 8,  clampUnit(c[2]) * 255f);
                colorBuffer.write(base + 12, clampUnit(c[3]) * 255f);
            }
            PointAttribute colorAttr = new PointAttribute(
                    AttributeType.COLOR, DataType.FLOAT32, 4, false, 16, 0, colorBuffer);
            colorAttr.setNumUniqueEntries(numVertices);
            dracoMesh.addAttribute(colorAttr);
        }

        // Feature-index attribute (GENERIC, INT32, 1 component per vertex).
        // setUniqueId must be called AFTER addAttribute: Drako's addAttribute
        // overwrites uniqueId with the attribute's positional index, so an
        // earlier call would be silently lost — and the injected metadata
        // block (keyed by FEATURE_INDEX_UID) would then map to the wrong
        // attribute slot.
        DataBuffer intBuffer = new DataBuffer();
        intBuffer.setCapacity(numVertices * 4);
        for (int i = 0; i < numVertices; i++) {
            intBuffer.write(i * 4, featureIndices[i]);
        }
        PointAttribute featureAttr = new PointAttribute(
                AttributeType.GENERIC, DataType.INT32, 1, false, 4, 0, intBuffer);
        featureAttr.setNumUniqueEntries(numVertices);
        dracoMesh.addAttribute(featureAttr);
        featureAttr.setUniqueId(FEATURE_INDEX_UID);

        // Faces: triangle soup — sequential indices (0,1,2), (3,4,5), ...
        for (int t = 0; t < numTriangles; t++) {
            dracoMesh.addFace(new int[]{t * 3, t * 3 + 1, t * 3 + 2});
        }

        DracoEncodeOptions options = new DracoEncodeOptions();
        options.setPositionBits(14);
        if (hasNormal) {
            options.setNormalBits(10);
        }
        if (hasUV) {
            options.setTextureCoordinateBits(12);
        }
        if (hasColor) {
            options.setColorBits(COLOR_QUANT_BITS);
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

    private static float clampUnit(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
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
