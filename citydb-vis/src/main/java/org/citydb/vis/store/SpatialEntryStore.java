/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.store;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Sharded disk-backed storage for {@link SpatialEntry} records.
 * <p>
 * Each entry is a fixed-size 88-byte record (no length prefix needed),
 * enabling O(1) random access by offset and efficient sequential scans.
 * Follows the same shard-per-core pattern as {@link ShardedMeshStore} to
 * eliminate write-phase lock contention.
 * <p>
 * <b>Write phase:</b> concurrent append via {@link #store} — shard selected
 * by {@code Math.floorMod(shardHint, shardCount)}. Each shard has an
 * independent {@link FileChannel} with a synchronized write position.
 * <p>
 * <b>Close phase:</b> sequential iteration via {@link #iterator()} reads
 * entries shard by shard in 4096-entry chunks (~360 KB) for optimal
 * sequential I/O throughput. No entries are held on the Java heap beyond
 * the current chunk.
 */
public class SpatialEntryStore implements Closeable {
    /**
     * Fixed record size in bytes:
     * id(8) + centerX(8) + centerY(8) + bbox[6](48) + meshHandle(8) + attrOffset(8)
     */
    static final int RECORD_SIZE = 88;
    private static final int CHUNK_ENTRIES = 4096;

    private final Shard[] shards;
    private final int shardCount;

    public SpatialEntryStore(int shardCount, Path tempDir) throws IOException {
        this.shardCount = shardCount;
        this.shards = new Shard[shardCount];
        try {
            for (int i = 0; i < shardCount; i++) {
                shards[i] = new Shard(tempDir);
            }
        } catch (IOException e) {
            close();
            throw e;
        }
    }

    /**
     * Store a spatial entry in the shard selected by {@code shardHint}.
     */
    public void store(SpatialEntry entry, int shardHint) throws IOException {
        int shard = Math.floorMod(shardHint, shardCount);
        shards[shard].store(entry);
    }

    /**
     * Total number of entries across all shards.
     */
    public long entryCount() {
        long total = 0;
        for (Shard shard : shards) {
            total += shard.entryCount();
        }
        return total;
    }

    /**
     * Sequential iterator over all stored entries. Reads entries shard by
     * shard in chunks for efficient I/O. Must only be called after the write
     * phase is complete (no concurrent stores).
     */
    public Iterator<SpatialEntry> iterator() {
        return new ShardIterator();
    }

    @Override
    public void close() throws IOException {
        IOException first = null;
        for (Shard shard : shards) {
            if (shard != null) {
                try {
                    shard.close();
                } catch (IOException e) {
                    if (first == null) first = e;
                }
            }
        }
        if (first != null) throw first;
    }

    // ---- Single shard ---------------------------------------------------

    private static class Shard implements Closeable {
        private final Path tempFile;
        private final FileChannel channel;
        private long writePosition = 0;

        Shard(Path tempDir) throws IOException {
            tempFile = Files.createTempFile(tempDir, "i3s-spatial-", ".bin");
            tempFile.toFile().deleteOnExit();
            channel = FileChannel.open(tempFile,
                    StandardOpenOption.READ, StandardOpenOption.WRITE);
        }

        synchronized void store(SpatialEntry entry) throws IOException {
            ByteBuffer buf = ByteBuffer.allocate(RECORD_SIZE)
                    .order(ByteOrder.LITTLE_ENDIAN);
            buf.putLong(entry.id());
            buf.putDouble(entry.centerX());
            buf.putDouble(entry.centerY());
            double[] bbox = entry.bbox();
            for (int i = 0; i < 6; i++) {
                buf.putDouble(bbox[i]);
            }
            buf.putLong(entry.meshHandle());
            buf.putLong(entry.attrOffset());
            buf.flip();

            long pos = writePosition;
            while (buf.hasRemaining()) {
                pos += channel.write(buf, pos);
            }
            writePosition = pos;
        }

        synchronized long entryCount() {
            return writePosition / RECORD_SIZE;
        }

        /**
         * Read a chunk of entries starting at the given file offset.
         * Returns the number of entries actually read (may be less than
         * requested at end of file). Thread-safe — uses positional reads.
         */
        int readChunk(long fileOffset, ByteBuffer buf) throws IOException {
            buf.clear();
            int totalRead = 0;
            int needed = buf.capacity();
            // Do not read past the written data
            synchronized (this) {
                long available = writePosition - fileOffset;
                if (available <= 0) return 0;
                needed = (int) Math.min(needed, available);
            }
            buf.limit(needed);
            while (totalRead < needed) {
                int read = channel.read(buf, fileOffset + totalRead);
                if (read < 0) break;
                totalRead += read;
            }
            return totalRead / RECORD_SIZE;
        }

        @Override
        public void close() throws IOException {
            if (channel.isOpen()) {
                channel.close();
            }
            Files.deleteIfExists(tempFile);
        }
    }

    // ---- Iterator -------------------------------------------------------

    private class ShardIterator implements Iterator<SpatialEntry> {
        private final ByteBuffer buf;
        private int currentShard = 0;
        private long shardFileOffset = 0;
        private int bufferEntries = 0;
        private int bufferIndex = 0;

        ShardIterator() {
            buf = ByteBuffer.allocate(RECORD_SIZE * CHUNK_ENTRIES)
                    .order(ByteOrder.LITTLE_ENDIAN);
            loadNextChunk();
        }

        @Override
        public boolean hasNext() {
            return bufferIndex < bufferEntries;
        }

        @Override
        public SpatialEntry next() {
            if (!hasNext()) throw new NoSuchElementException();

            long id = buf.getLong();
            double centerX = buf.getDouble();
            double centerY = buf.getDouble();
            double[] bbox = new double[6];
            for (int i = 0; i < 6; i++) {
                bbox[i] = buf.getDouble();
            }
            long meshHandle = buf.getLong();
            long attrOffset = buf.getLong();

            bufferIndex++;
            if (bufferIndex >= bufferEntries) {
                loadNextChunk();
            }

            return new SpatialEntry(id, centerX, centerY, bbox, meshHandle, attrOffset);
        }

        private void loadNextChunk() {
            while (currentShard < shardCount) {
                try {
                    int read = shards[currentShard].readChunk(shardFileOffset, buf);
                    if (read > 0) {
                        buf.flip();
                        bufferEntries = read;
                        bufferIndex = 0;
                        shardFileOffset += (long) read * RECORD_SIZE;
                        return;
                    }
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
                // Shard exhausted — advance to next
                currentShard++;
                shardFileOffset = 0;
            }
            // All shards exhausted
            bufferEntries = 0;
            bufferIndex = 0;
        }
    }
}
