/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Disk-backed mapping from scene node index to {@link NodeEntry} lists.
 * <p>
 * After quadtree construction, each leaf node's compact entries (id,
 * meshHandle, attrOffset) are written here sequentially. During the output
 * phase, entries are loaded per-node on demand via positional reads —
 * thread-safe for parallel node processing.
 * <p>
 * Index structure: parallel {@code long[]} and {@code int[]} arrays mapping
 * nodeIndex → (fileOffset, entryCount). At 2M nodes this costs ~24 MB,
 * compared to ~4 GB for in-memory {@code Map<Integer, List<NodeEntry>>}.
 */
public class NodeEntryStore implements Closeable {
    static final int NODE_ENTRY_SIZE = 24; // id(8) + meshHandle(8) + attrOffset(8)

    private final Path tempFile;
    private final FileChannel channel;
    private long writePosition = 0;

    private long[] nodeOffsets;
    private int[] nodeCounts;
    private int capacity;

    public NodeEntryStore(Path tempDir, int initialCapacity) throws IOException {
        this.capacity = initialCapacity;
        this.nodeOffsets = new long[initialCapacity];
        this.nodeCounts = new int[initialCapacity];
        Arrays.fill(nodeOffsets, -1L);

        tempFile = Files.createTempFile(tempDir, "vis-nodeentry-", ".bin");
        tempFile.toFile().deleteOnExit();
        channel = FileChannel.open(tempFile,
                StandardOpenOption.READ, StandardOpenOption.WRITE);
    }

    /**
     * Write all entries for a node. Must be called sequentially (not
     * thread-safe for concurrent writes to the same store).
     */
    public void writeNode(int nodeIndex, List<NodeEntry> entries) throws IOException {
        if (entries.isEmpty()) {
            return;
        }

        ensureCapacity(nodeIndex + 1);
        long startPos = writePosition;
        int count = entries.size();

        ByteBuffer buf = BufferUtils.allocateLittleEndian(count * NODE_ENTRY_SIZE);
        for (NodeEntry e : entries) {
            buf.putLong(e.id());
            buf.putLong(e.meshHandle());
            buf.putLong(e.attrOffset());
        }
        buf.flip();

        long pos = startPos;
        while (buf.hasRemaining()) {
            pos += channel.write(buf, pos);
        }
        writePosition = pos;

        nodeOffsets[nodeIndex] = startPos;
        nodeCounts[nodeIndex] = count;
    }

    /**
     * Load entries for a node. Thread-safe — uses positional reads.
     */
    public List<NodeEntry> loadNode(int nodeIndex) throws IOException {
        if (nodeIndex >= capacity || nodeCounts[nodeIndex] == 0) {
            return List.of();
        }

        int count = nodeCounts[nodeIndex];
        long offset = nodeOffsets[nodeIndex];

        ByteBuffer buf = BufferUtils.allocateLittleEndian(count * NODE_ENTRY_SIZE);
        FileHelper.readFully(channel, buf, offset);
        buf.flip();

        List<NodeEntry> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(new NodeEntry(buf.getLong(), buf.getLong(), buf.getLong()));
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

    private void ensureCapacity(int minCapacity) {
        if (minCapacity > capacity) {
            int newCapacity = Math.max(minCapacity, capacity + (capacity >> 1));
            nodeOffsets = Arrays.copyOf(nodeOffsets, newCapacity);
            // New slots in nodeCounts default to 0 (no entries)
            nodeCounts = Arrays.copyOf(nodeCounts, newCapacity);
            // New slots in nodeOffsets need -1 sentinel
            Arrays.fill(nodeOffsets, capacity, newCapacity, -1L);
            capacity = newCapacity;
        }
    }

}
