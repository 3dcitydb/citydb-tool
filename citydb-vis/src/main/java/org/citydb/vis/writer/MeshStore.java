/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.writer;

import org.citydb.vis.geometry.TriangleMesh;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

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

    MeshStore() throws IOException {
        tempFile = Files.createTempFile("i3s-mesh-", ".bin");
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

    /**
     * Load a mesh previously written with {@link #store(TriangleMesh)}.
     * Thread-safe — uses positional reads that do not mutate channel state.
     */
    TriangleMesh load(long handle) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        readFully(header, handle);
        header.flip();
        int dataSize = header.getInt();

        ByteBuffer buf = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN);
        readFully(buf, handle + 4);
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
     *   int32  triangleCount
     *   int32[tc*3]    triangles (v0,v1,v2 interleaved)
     *   int64[tc]      featureIds
     * </pre>
     */
    private static ByteBuffer serialize(TriangleMesh mesh) {
        List<double[]> positions = mesh.getPositions();
        List<float[]> normals = mesh.getNormals();
        List<float[]> texCoords = mesh.getTexCoords();
        List<int[]> triangles = mesh.getTriangles();
        List<Long> featureIds = mesh.getFeatureIds();
        boolean hasTC = mesh.hasTexCoords();

        int vc = positions.size();
        int tc = triangles.size();
        int dataSize = 4 + vc * 24 + vc * 12 + 1
                + (hasTC ? vc * 8 : 0)
                + 4 + tc * 12 + tc * 8 + tc * 4;

        ByteBuffer buf = ByteBuffer.allocate(4 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
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

        buf.flip();
        return buf;
    }

    private static TriangleMesh deserialize(ByteBuffer buf) {
        TriangleMesh mesh = new TriangleMesh();
        int vc = buf.getInt();
        List<double[]> positions = mesh.getPositions();
        for (int i = 0; i < vc; i++) {
            positions.add(new double[]{buf.getDouble(), buf.getDouble(), buf.getDouble()});
        }
        List<float[]> normals = mesh.getNormals();
        for (int i = 0; i < vc; i++) {
            normals.add(new float[]{buf.getFloat(), buf.getFloat(), buf.getFloat()});
        }
        boolean hasTC = buf.get() != 0;
        if (hasTC) {
            mesh.setHasTexCoords(true);
        }
        List<float[]> texCoords = mesh.getTexCoords();
        if (hasTC) {
            for (int i = 0; i < vc; i++) {
                texCoords.add(new float[]{buf.getFloat(), buf.getFloat()});
            }
        } else {
            for (int i = 0; i < vc; i++) {
                texCoords.add(new float[]{0f, 0f});
            }
        }
        int tc = buf.getInt();
        List<int[]> triangles = mesh.getTriangles();
        List<Long> featureIds = mesh.getFeatureIds();
        for (int i = 0; i < tc; i++) {
            triangles.add(new int[]{buf.getInt(), buf.getInt(), buf.getInt()});
        }
        for (int i = 0; i < tc; i++) {
            featureIds.add(buf.getLong());
        }
        List<Integer> triTexIds = mesh.getTriangleTextureIds();
        for (int i = 0; i < tc; i++) {
            triTexIds.add(buf.getInt());
        }
        return mesh;
    }

    private void readFully(ByteBuffer buf, long startPosition) throws IOException {
        int totalRead = 0;
        int needed = buf.remaining();
        while (totalRead < needed) {
            int read = channel.read(buf, startPosition + totalRead);
            if (read < 0) {
                throw new IOException("Unexpected end of mesh store file");
            }
            totalRead += read;
        }
    }
}
