/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.store;

import org.citydb.vis.util.BufferUtils;
import org.citydb.vis.util.FileHelper;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Disk-backed storage for GPU-instancing payloads: one fixed-size record per
 * implicit-geometry instance holding the prototype reference and the decomposed
 * per-instance placement (anchor + rotation + scale).
 * <p>
 * Instances piggyback on the existing spatial pipeline as ordinary
 * {@link SpatialEntry} records whose {@code meshHandle} is a <b>negative</b>
 * encoded record index into this store (see {@link #toHandle}), so extent
 * computation, grid partitioning, tree building, and the split stages move
 * them around without knowing they carry no mesh blob. The only two places
 * that dereference a {@code meshHandle} — {@code NodeAssembler} and
 * {@code AtlasOverflowSplitStage} — branch on {@link #isInstanceHandle}
 * (the latter only implicitly: instances never set the per-feature texture
 * flag, so the atlas stage's textured-node filter skips them before any
 * mesh load).
 * <p>
 * Writes are synchronized; reads use positional I/O and are thread-safe.
 */
public class InstanceStore implements Closeable {
    // prototypeId(4) + anchor lon/lat/z (3×8) + rotation xyzw (4×4)
    // + scale xyz (3×4) + style rgba (4×4)
    static final int RECORD_SIZE = 72;

    private final Path tempFile;
    private final FileChannel channel;
    private long recordCount = 0;

    /**
     * Placement payload of one instance.
     *
     * @param prototypeId registry id of the shared template
     * @param anchorX     anchor longitude (degrees, EPSG:4326)
     * @param anchorY     anchor latitude (degrees)
     * @param anchorZ     anchor height (meters, possibly clamped to ground)
     * @param rotation    unit quaternion [x, y, z, w], prototype-local → ENU
     * @param scale       per-axis scale [sx, sy, sz]
     * @param styleColor  sRGB RGBA of the parent feature type's resolved
     *                    {@code DefaultObjectStyle}, captured at ingest while
     *                    the qualified type name (with namespace) is still
     *                    available; drives the instanced node's plain material
     */
    public record InstanceRecord(int prototypeId,
                                 double anchorX, double anchorY, double anchorZ,
                                 float[] rotation, float[] scale, float[] styleColor) {
    }

    public InstanceStore(Path tempDir) throws IOException {
        tempFile = Files.createTempFile(tempDir, "vis-instance-", ".bin");
        tempFile.toFile().deleteOnExit();
        channel = FileChannel.open(tempFile,
                StandardOpenOption.READ, StandardOpenOption.WRITE);
    }

    /** Encode a record index as a negative pseudo mesh handle. */
    public static long toHandle(long recordIndex) {
        return -recordIndex - 1;
    }

    /** Whether the given mesh handle is an encoded instance record index. */
    public static boolean isInstanceHandle(long meshHandle) {
        return meshHandle < 0;
    }

    /** Decode a pseudo mesh handle back into a record index. */
    public static long toIndex(long meshHandle) {
        return -meshHandle - 1;
    }

    /**
     * Store an instance record and return its record index (encode it with
     * {@link #toHandle} before writing it into a {@link SpatialEntry}).
     */
    public synchronized long store(InstanceRecord record) throws IOException {
        ByteBuffer buf = BufferUtils.allocateLittleEndian(RECORD_SIZE);
        buf.putInt(record.prototypeId());
        buf.putDouble(record.anchorX());
        buf.putDouble(record.anchorY());
        buf.putDouble(record.anchorZ());
        for (int i = 0; i < 4; i++) {
            buf.putFloat(record.rotation()[i]);
        }
        for (int i = 0; i < 3; i++) {
            buf.putFloat(record.scale()[i]);
        }
        for (int i = 0; i < 4; i++) {
            buf.putFloat(record.styleColor()[i]);
        }
        buf.flip();

        long index = recordCount;
        long pos = index * RECORD_SIZE;
        while (buf.hasRemaining()) {
            pos += channel.write(buf, pos);
        }
        recordCount++;
        return index;
    }

    /** Load a record by index. Thread-safe — uses positional reads. */
    public InstanceRecord load(long recordIndex) throws IOException {
        ByteBuffer buf = BufferUtils.allocateLittleEndian(RECORD_SIZE);
        FileHelper.readFully(channel, buf, recordIndex * RECORD_SIZE);
        buf.flip();

        int prototypeId = buf.getInt();
        double anchorX = buf.getDouble();
        double anchorY = buf.getDouble();
        double anchorZ = buf.getDouble();
        float[] rotation = new float[4];
        for (int i = 0; i < 4; i++) {
            rotation[i] = buf.getFloat();
        }
        float[] scale = new float[3];
        for (int i = 0; i < 3; i++) {
            scale[i] = buf.getFloat();
        }
        float[] styleColor = new float[4];
        for (int i = 0; i < 4; i++) {
            styleColor[i] = buf.getFloat();
        }
        return new InstanceRecord(prototypeId, anchorX, anchorY, anchorZ, rotation, scale,
                styleColor);
    }

    @Override
    public void close() throws IOException {
        if (channel.isOpen()) {
            channel.close();
        }
        Files.deleteIfExists(tempFile);
    }
}
