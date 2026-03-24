/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.writer;

import org.citydb.vis.geometry.TriangleMesh;

import java.io.Closeable;
import java.io.IOException;

/**
 * Sharded mesh storage that distributes writes across multiple {@link MeshStore}
 * instances to eliminate synchronized lock contention.
 * <p>
 * Each shard has its own temp file and independent write lock. Using
 * {@code featureId % shardCount} for shard selection ensures that concurrent
 * writer threads rarely contend on the same shard.
 * <p>
 * Handles encode both the shard index and the file offset:
 * upper 16 bits = shardId, lower 48 bits = offset within the shard file.
 * This supports up to 65,536 shards and 256 TB per shard.
 */
class ShardedMeshStore implements Closeable {
    private final MeshStore[] shards;
    private final int shardCount;

    ShardedMeshStore(int shardCount) throws IOException {
        this.shardCount = shardCount;
        this.shards = new MeshStore[shardCount];
        try {
            for (int i = 0; i < shardCount; i++) {
                shards[i] = new MeshStore();
            }
        } catch (IOException e) {
            // Clean up any shards that were successfully created
            close();
            throw e;
        }
    }

    /**
     * Store a mesh in the shard selected by {@code shardHint}.
     * Returns an encoded handle (shardId + offset) for later retrieval.
     */
    long store(TriangleMesh mesh, int shardHint) throws IOException {
        int shard = Math.abs(shardHint % shardCount);
        long offset = shards[shard].store(mesh);
        return encodeHandle(shard, offset);
    }

    /**
     * Load a mesh from the encoded handle. Thread-safe — uses positional reads.
     */
    TriangleMesh load(long handle) throws IOException {
        int shard = decodeShardId(handle);
        long offset = decodeOffset(handle);
        return shards[shard].load(offset);
    }

    @Override
    public void close() throws IOException {
        IOException first = null;
        for (MeshStore shard : shards) {
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

    static long encodeHandle(int shardId, long offset) {
        return ((long) shardId << 48) | (offset & 0xFFFFFFFFFFFFL);
    }

    static int decodeShardId(long handle) {
        return (int) (handle >>> 48);
    }

    static long decodeOffset(long handle) {
        return handle & 0xFFFFFFFFFFFFL;
    }
}
