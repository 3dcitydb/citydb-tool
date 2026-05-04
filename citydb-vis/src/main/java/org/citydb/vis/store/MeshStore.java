/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.store;

import org.citydb.model.common.Name;
import org.citydb.vis.geometry.TriangleMesh;
import org.citydb.vis.util.BufferUtils;
import org.citydb.vis.util.FileHelper;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Disk-backed storage for {@link TriangleMesh} instances.
 * <p>
 * During the write phase, meshes are serialized into a temporary file so that
 * the JVM heap only holds lightweight feature metadata. During the close phase
 * meshes are loaded back per-node for geometry encoding and then discarded.
 * <p>
 * Writes are synchronized (single writer); reads use positional I/O on the
 * {@link FileChannel} and are safe for concurrent access from multiple threads.
 */
class MeshStore implements Closeable {
    private final Path tempFile;
    private final FileChannel channel;
    private long writePosition = 0;
    private volatile boolean hasColors;

    MeshStore(Path tempDir) throws IOException {
        tempFile = Files.createTempFile(tempDir, "vis-mesh-", ".bin");
        tempFile.toFile().deleteOnExit();
        channel = FileChannel.open(tempFile,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE);
    }

    /**
     * Serialize a mesh to the store and return a handle (file offset) that
     * can later be passed to {@link #load(long)}.
     */
    long store(TriangleMesh mesh) throws IOException {
        ByteBuffer buf = serialize(mesh);
        if (mesh.hasColors()) {
            hasColors = true;
        }
        synchronized (this) {
            long startPos = writePosition;
            long filePos = startPos;
            while (buf.hasRemaining()) {
                filePos += channel.write(buf, filePos);
            }
            writePosition = filePos;
            return startPos;
        }
    }

    boolean hasColors() {
        return hasColors;
    }

    /**
     * Load a mesh previously written with {@link #store(TriangleMesh)}.
     * Thread-safe — uses positional reads that do not mutate channel state.
     */
    TriangleMesh load(long handle) throws IOException {
        ByteBuffer header = BufferUtils.allocateLittleEndian(4);
        FileHelper.readFully(channel, header, handle);
        header.flip();
        int dataSize = header.getInt();

        ByteBuffer buf = BufferUtils.allocateLittleEndian(dataSize);
        FileHelper.readFully(channel, buf, handle + 4);
        buf.flip();

        return deserialize(buf);
    }

    @Override
    public void close() throws IOException {
        if (channel.isOpen()) {
            channel.close();
        }
        Files.deleteIfExists(tempFile);
    }

    // ---- serialization ------------------------------------------------

    /**
     * Binary format:
     * <pre>
     *   int32  dataSize          (byte count of everything after this field)
     *   int32  vertexCount
     *   float64[vc*3]  positions (x,y,z interleaved)
     *   float32[vc*3]  normals   (nx,ny,nz interleaved)
     *   byte   hasTexCoords      (0 or 1)
     *   if hasTexCoords:
     *     float32[vc*2]  texCoords (u,v interleaved)
     *   byte   hasColors         (0 or 1)
     *   if hasColors:
     *     float32[vc*4]  colors  (r,g,b,a interleaved)
     *   int32  triangleCount
     *   int32[tc*3]    triangles (v0,v1,v2 interleaved)
     *   int64[tc]      featureIds
     *   int32[tc]      triTexIds (&lt; 0 = untextured triangle)
     *   if hasColors:
     *     byte[(tc+7)/8] triColored (LSB-first bit-packed; bit i is in byte i/8 at bit i%8)
     *   int16  surfaceTypeCount  (S — typically 1..10, capped at 32767)
     *   for each of S types:
     *     int32 localNameLen + bytes localName  (UTF-8)
     *     int32 namespaceLen + bytes namespace  (UTF-8, may be empty)
     *   int16[tc] surfaceTypeIds (index into the per-mesh dictionary)
     * </pre>
     * The surface-type dictionary is per-mesh and tiny in practice (a few
     * distinct types per feature) so an int16 index is sufficient and keeps
     * the per-triangle storage cost to 2 bytes.
     */
    private static ByteBuffer serialize(TriangleMesh mesh) {
        List<double[]> positions = mesh.getPositions();
        List<float[]> normals = mesh.getNormals();
        List<float[]> texCoords = mesh.getTexCoords();
        List<float[]> colors = mesh.getColors();
        List<int[]> triangles = mesh.getTriangles();
        List<Long> featureIds = mesh.getFeatureIds();
        boolean hasTC = mesh.hasTexCoords();
        boolean hasCol = mesh.hasColors();

        int vc = positions.size();
        int tc = triangles.size();
        // Bit-packed colored bits, only when hasCol. One bit per triangle.
        int coloredBytes = hasCol ? ((tc + 7) >>> 3) : 0;

        // Build the per-mesh surface-type dictionary up front so we can size
        // the buffer correctly. Using a LinkedHashMap-like pattern via a
        // HashMap<Name, Integer> + List<Name> preserves first-encounter order.
        Map<Name, Integer> typeIndex = new HashMap<>();
        List<Name> typeDict = new ArrayList<>();
        short[] surfaceTypeIds = new short[tc];
        for (int i = 0; i < tc; i++) {
            Name type = mesh.getTriangleSurfaceType(i);
            Integer idx = typeIndex.get(type);
            if (idx == null) {
                if (typeDict.size() >= Short.MAX_VALUE) {
                    throw new IllegalStateException(
                            "Mesh exceeds " + Short.MAX_VALUE + " distinct surface types.");
                }
                idx = typeDict.size();
                typeIndex.put(type, idx);
                typeDict.add(type);
            }
            surfaceTypeIds[i] = idx.shortValue();
        }
        long dictBytes = 2L; // surfaceTypeCount field
        for (Name type : typeDict) {
            dictBytes += 4L + utf8Len(type.getLocalName());
            dictBytes += 4L + utf8Len(type.getNamespace());
        }

        // Compute in long space and check for overflow before narrowing. A
        // single mesh this large is unexpected, but silent truncation would
        // corrupt the entire store, so fail loudly instead.
        long dataSizeLong = 4L + (long) vc * 24 + (long) vc * 12 + 1L
                + (hasTC ? (long) vc * 8 : 0)
                + 1L + (hasCol ? (long) vc * 16 : 0)
                + 4L + (long) tc * 12 + (long) tc * 8 + (long) tc * 4
                + coloredBytes
                + dictBytes + (long) tc * 2;
        if (dataSizeLong > Integer.MAX_VALUE - 4) {
            throw new IllegalStateException("Mesh serialized size exceeds 2 GB: " + dataSizeLong);
        }
        int dataSize = (int) dataSizeLong;

        ByteBuffer buf = BufferUtils.allocateLittleEndian(4 + dataSize);
        buf.putInt(dataSize);
        buf.putInt(vc);
        for (double[] p : positions) {
            buf.putDouble(p[0]);
            buf.putDouble(p[1]);
            buf.putDouble(p[2]);
        }
        for (float[] n : normals) {
            buf.putFloat(n[0]);
            buf.putFloat(n[1]);
            buf.putFloat(n[2]);
        }
        buf.put((byte) (hasTC ? 1 : 0));
        if (hasTC) {
            for (float[] uv : texCoords) {
                buf.putFloat(uv[0]);
                buf.putFloat(uv[1]);
            }
        }
        buf.put((byte) (hasCol ? 1 : 0));
        if (hasCol) {
            for (float[] c : colors) {
                buf.putFloat(c[0]);
                buf.putFloat(c[1]);
                buf.putFloat(c[2]);
                buf.putFloat(c[3]);
            }
        }
        buf.putInt(tc);
        for (int[] t : triangles) {
            buf.putInt(t[0]);
            buf.putInt(t[1]);
            buf.putInt(t[2]);
        }
        for (long fid : featureIds) {
            buf.putLong(fid);
        }
        List<Integer> triTexIds = mesh.getTriangleTextureIds();
        for (int texId : triTexIds) {
            buf.putInt(texId);
        }
        if (hasCol) {
            byte[] packed = new byte[coloredBytes];
            for (int i = 0; i < tc; i++) {
                if (mesh.isTriangleColored(i)) {
                    packed[i >>> 3] |= (byte) (1 << (i & 7));
                }
            }
            buf.put(packed);
        }

        // Surface-type dictionary + per-triangle indices. Always present
        // (even when only one distinct type exists); empty meshes skip
        // serialization entirely so this field count is consistent.
        buf.putShort((short) typeDict.size());
        for (Name type : typeDict) {
            writeUtf8(buf, type.getLocalName());
            writeUtf8(buf, type.getNamespace());
        }
        for (short id : surfaceTypeIds) {
            buf.putShort(id);
        }

        buf.flip();
        return buf;
    }

    private static int utf8Len(String s) {
        return s == null ? 0 : s.getBytes(StandardCharsets.UTF_8).length;
    }

    private static void writeUtf8(ByteBuffer buf, String s) {
        byte[] bytes = (s == null ? "" : s).getBytes(StandardCharsets.UTF_8);
        buf.putInt(bytes.length);
        buf.put(bytes);
    }

    private static String readUtf8(ByteBuffer buf) {
        int len = buf.getInt();
        byte[] bytes = new byte[len];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static TriangleMesh deserialize(ByteBuffer buf) {
        TriangleMesh mesh = new TriangleMesh();
        int vc = buf.getInt();
        // Serialized layout keeps positions, normals, uvs and colors in
        // separate contiguous blocks, so we read them into tmp arrays and
        // then feed mesh.addVertex to preserve the size-equality invariants.
        double[][] positions = new double[vc][3];
        for (int i = 0; i < vc; i++) {
            positions[i][0] = buf.getDouble();
            positions[i][1] = buf.getDouble();
            positions[i][2] = buf.getDouble();
        }
        float[][] normals = new float[vc][3];
        for (int i = 0; i < vc; i++) {
            normals[i][0] = buf.getFloat();
            normals[i][1] = buf.getFloat();
            normals[i][2] = buf.getFloat();
        }
        boolean hasTC = buf.get() != 0;
        float[][] texCoords = null;
        if (hasTC) {
            texCoords = new float[vc][2];
            for (int i = 0; i < vc; i++) {
                texCoords[i][0] = buf.getFloat();
                texCoords[i][1] = buf.getFloat();
            }
        }
        boolean hasCol = buf.get() != 0;
        float[][] colors = null;
        if (hasCol) {
            colors = new float[vc][4];
            for (int i = 0; i < vc; i++) {
                colors[i][0] = buf.getFloat();
                colors[i][1] = buf.getFloat();
                colors[i][2] = buf.getFloat();
                colors[i][3] = buf.getFloat();
            }
        }
        for (int i = 0; i < vc; i++) {
            double[] p = positions[i];
            float[] n = normals[i];
            if (hasTC && hasCol) {
                float[] uv = texCoords[i], c = colors[i];
                mesh.addVertex(p[0], p[1], p[2], n[0], n[1], n[2],
                        uv[0], uv[1], c[0], c[1], c[2], c[3]);
            } else if (hasTC) {
                float[] uv = texCoords[i];
                mesh.addVertex(p[0], p[1], p[2], n[0], n[1], n[2], uv[0], uv[1]);
            } else if (hasCol) {
                float[] c = colors[i];
                mesh.addVertex(p[0], p[1], p[2], n[0], n[1], n[2],
                        c[0], c[1], c[2], c[3]);
            } else {
                mesh.addVertex(p[0], p[1], p[2], n[0], n[1], n[2]);
            }
        }

        int tc = buf.getInt();
        // Triangles, featureIds, triTexIds and the optional colored bitmap
        // are stored in four contiguous blocks; stash the first three so
        // mesh.addTriangle can receive all of them along with the colored bit.
        int[][] triangles = new int[tc][3];
        for (int i = 0; i < tc; i++) {
            triangles[i][0] = buf.getInt();
            triangles[i][1] = buf.getInt();
            triangles[i][2] = buf.getInt();
        }
        long[] featureIds = new long[tc];
        for (int i = 0; i < tc; i++) {
            featureIds[i] = buf.getLong();
        }
        int[] texIds = new int[tc];
        for (int i = 0; i < tc; i++) {
            texIds[i] = buf.getInt();
        }
        byte[] colored = null;
        if (hasCol) {
            colored = new byte[(tc + 7) >>> 3];
            buf.get(colored);
        }

        // Read surface-type dictionary + per-triangle indices, then resolve
        // each id back to its Name before feeding mesh.addTriangle.
        int typeCount = buf.getShort();
        Name[] typeDict = new Name[typeCount];
        for (int i = 0; i < typeCount; i++) {
            String localName = readUtf8(buf);
            String namespace = readUtf8(buf);
            typeDict[i] = Name.of(localName, namespace);
        }
        short[] surfaceTypeIds = new short[tc];
        for (int i = 0; i < tc; i++) {
            surfaceTypeIds[i] = buf.getShort();
        }

        for (int i = 0; i < tc; i++) {
            boolean isColored = colored != null && (colored[i >>> 3] & (1 << (i & 7))) != 0;
            mesh.addTriangle(triangles[i][0], triangles[i][1], triangles[i][2],
                    featureIds[i], texIds[i], isColored, typeDict[surfaceTypeIds[i]]);
        }
        return mesh;
    }

}
