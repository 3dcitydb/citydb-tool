/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.store;

import org.citydb.vis.util.FileHelper;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Disk-backed spatial partitioning of {@link SpatialEntry} records.
 * <p>
 * Entries from a {@link SpatialEntryStore} are redistributed into a single
 * flat file where each grid cell's records are stored contiguously. This
 * enables per-cell loading without materializing all entries on the heap.
 * <p>
 * Construction uses a two-pass approach:
 * <ol>
 *   <li><b>Histogram pass</b>: stream all entries, count per cell.</li>
 *   <li><b>Scatter pass</b>: stream again, write each entry to its cell's
 *       pre-allocated file segment.</li>
 * </ol>
 * Both passes are sequential reads of the source store — optimal I/O.
 */
public class PartitionedEntryStore implements Closeable {

    private record CellSegment(long fileOffset, int count) {
    }

    private final Path tempFile;
    private final FileChannel channel;
    private final Map<Long, CellSegment> cellIndex;

    private PartitionedEntryStore(Path tempFile, FileChannel channel,
                                  Map<Long, CellSegment> cellIndex) {
        this.tempFile = tempFile;
        this.channel = channel;
        this.cellIndex = cellIndex;
    }

    /**
     * Partition entries from the source store into grid cells on disk.
     *
     * @param source  the spatial entry store (write phase must be complete)
     * @param extent  global bounding box [minX, minY, minZ, maxX, maxY, maxZ]
     * @param gridDim number of grid divisions along each axis
     * @param tempDir directory for the temporary partition file
     * @return a partitioned store with per-cell contiguous segments
     */
    public static PartitionedEntryStore create(SpatialEntryStore source,
                                               double[] extent, int gridDim,
                                               Path tempDir) throws IOException {
        double rangeX = extent[3] - extent[0];
        double rangeY = extent[4] - extent[1];
        double cellWidth = rangeX > 0 ? rangeX / gridDim : 1;
        double cellHeight = rangeY > 0 ? rangeY / gridDim : 1;

        // --- Pass 1: histogram ---
        Map<Long, int[]> histogram = new HashMap<>();
        Iterator<SpatialEntry> it = source.iterator();
        while (it.hasNext()) {
            SpatialEntry e = it.next();
            long cellKey = cellKey(e, extent, gridDim, cellWidth, cellHeight);
            histogram.computeIfAbsent(cellKey, k -> new int[1])[0]++;
        }

        // --- Compute cell file offsets ---
        Map<Long, CellSegment> cellIndex = new HashMap<>();
        Map<Long, long[]> writePositions = new HashMap<>();
        long cumulativeOffset = 0;
        for (Map.Entry<Long, int[]> entry : histogram.entrySet()) {
            long key = entry.getKey();
            int count = entry.getValue()[0];
            cellIndex.put(key, new CellSegment(cumulativeOffset, count));
            writePositions.put(key, new long[]{cumulativeOffset});
            cumulativeOffset += (long) count * SpatialEntryStore.RECORD_SIZE;
        }

        // --- Create output file ---
        Path tempFile = Files.createTempFile(tempDir, "vis-partitioned-", ".bin");
        tempFile.toFile().deleteOnExit();
        FileChannel channel = FileChannel.open(tempFile,
                StandardOpenOption.READ, StandardOpenOption.WRITE);

        // --- Pass 2: scatter ---
        try {
            ByteBuffer recordBuf = ByteBuffer.allocate(SpatialEntryStore.RECORD_SIZE)
                    .order(ByteOrder.LITTLE_ENDIAN);
            it = source.iterator();
            while (it.hasNext()) {
                SpatialEntry e = it.next();
                long cellKey = cellKey(e, extent, gridDim, cellWidth, cellHeight);
                long[] posHolder = writePositions.get(cellKey);
                long pos = posHolder[0];

                recordBuf.clear();
                recordBuf.putLong(e.id());
                recordBuf.putDouble(e.centerX());
                recordBuf.putDouble(e.centerY());
                double[] bbox = e.bbox();
                for (int i = 0; i < 6; i++) {
                    recordBuf.putDouble(bbox[i]);
                }
                recordBuf.putLong(e.meshHandle());
                recordBuf.putLong(e.attrOffset());
                recordBuf.flip();

                while (recordBuf.hasRemaining()) {
                    pos += channel.write(recordBuf, pos);
                }
                posHolder[0] = pos;
            }

            return new PartitionedEntryStore(tempFile, channel, cellIndex);
        } catch (IOException e) {
            try {
                channel.close();
            } catch (IOException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }
    }

    /**
     * The set of cell keys that contain at least one entry.
     */
    public Set<Long> cellKeys() {
        return cellIndex.keySet();
    }

    /**
     * Load all entries for a grid cell. Thread-safe — uses positional reads.
     */
    public List<SpatialEntry> loadCell(long cellKey) throws IOException {
        CellSegment seg = cellIndex.get(cellKey);
        if (seg == null || seg.count == 0) {
            return List.of();
        }

        int bytes = seg.count * SpatialEntryStore.RECORD_SIZE;
        ByteBuffer buf = ByteBuffer.allocate(bytes).order(ByteOrder.LITTLE_ENDIAN);
        FileHelper.readFully(channel, buf, seg.fileOffset);
        buf.flip();

        List<SpatialEntry> result = new ArrayList<>(seg.count);
        for (int i = 0; i < seg.count; i++) {
            long id = buf.getLong();
            double centerX = buf.getDouble();
            double centerY = buf.getDouble();
            double[] bbox = new double[6];
            for (int j = 0; j < 6; j++) {
                bbox[j] = buf.getDouble();
            }
            long meshHandle = buf.getLong();
            long attrOffset = buf.getLong();
            result.add(new SpatialEntry(id, centerX, centerY, bbox, meshHandle, attrOffset));
        }
        return result;
    }

    @Override
    public void close() throws IOException {
        if (channel.isOpen()) {
            channel.close();
        }
        Files.deleteIfExists(tempFile);
    }

    private static long cellKey(SpatialEntry entry, double[] extent,
                                int gridDim, double cellWidth, double cellHeight) {
        int gx = Math.max(0, Math.min(
                (int) ((entry.centerX() - extent[0]) / cellWidth), gridDim - 1));
        int gy = Math.max(0, Math.min(
                (int) ((entry.centerY() - extent[1]) / cellHeight), gridDim - 1));
        return (long) gy * gridDim + gx;
    }

}
