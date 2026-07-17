/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.store;

import org.citydb.vis.geometry.TriangleMesh;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Sharded mesh storage that distributes writes across multiple {@link MeshStore}
 * instances to eliminate synchronized lock contention.
 * <p>
 * Each shard has its own temp file and independent write lock. Using
 * {@code Math.floorMod(featureId, shardCount)} for shard selection ensures
 * that concurrent writer threads rarely contend on the same shard.
 * <p>
 * Handles encode both the shard index and the file offset:
 * upper 16 bits = shardId, lower 48 bits = offset within the shard file.
 * This supports up to 32,768 shards (the sign bit must stay clear — see
 * below) and 256 TB per shard.
 * <p>
 * <b>Handles are always non-negative.</b> {@link InstanceStore} encodes
 * GPU-instance records as <em>negative</em> pseudo mesh handles, so the sign
 * bit is the discriminator between the two stores throughout the pipeline
 * ({@code InstanceStore.isInstanceHandle}). The constructor enforces the
 * shard ceiling and {@link #load} rejects negative handles rather than
 * mis-reading an instance record as a shard/offset pair.
 */
public class ShardedMeshStore implements Closeable {
    /**
     * Shard ids at or above 2^15 would set bit 63 in {@code encodeHandle} and
     * produce a negative handle, colliding with {@link InstanceStore}'s
     * encoding. Public so callers deriving the shard count from user input
     * (thread count) can clamp before construction instead of failing here.
     */
    public static final int MAX_SHARDS = 1 << 15;

    private final MeshStore[] shards;
    private final int shardCount;

    public ShardedMeshStore(int shardCount, Path tempDir) throws IOException {
        if (shardCount < 1 || shardCount > MAX_SHARDS) {
            throw new IllegalArgumentException("Shard count must be in [1, " + MAX_SHARDS +
                    "] to keep mesh handles non-negative, but was " + shardCount + ".");
        }
        this.shardCount = shardCount;
        this.shards = new MeshStore[shardCount];
        try {
            for (int i = 0; i < shardCount; i++) {
                shards[i] = new MeshStore(tempDir);
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
    public long store(TriangleMesh mesh, int shardHint) throws IOException {
        // Math.floorMod handles negative hints and Integer.MIN_VALUE correctly,
        // unlike Math.abs(x % n) which returns a negative result when x == MIN_VALUE.
        int shard = Math.floorMod(shardHint, shardCount);
        long offset = shards[shard].store(mesh);
        return encodeHandle(shard, offset);
    }

    /**
     * Load a mesh from the encoded handle. Thread-safe — uses positional reads.
     */
    public TriangleMesh load(long handle) throws IOException {
        if (handle < 0) {
            throw new IllegalArgumentException("Negative mesh handle " + handle +
                    " — this is an InstanceStore pseudo handle, not a mesh handle.");
        }
        int shard = decodeShardId(handle);
        long offset = decodeOffset(handle);
        return shards[shard].load(offset);
    }

    /** True if any stored mesh carries vertex colors (X3DMaterial baking). */
    public boolean hasColors() {
        for (MeshStore shard : shards) {
            if (shard != null && shard.hasColors()) {
                return true;
            }
        }
        return false;
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

    private static long encodeHandle(int shardId, long offset) {
        return ((long) shardId << 48) | (offset & 0xFFFFFFFFFFFFL);
    }

    private static int decodeShardId(long handle) {
        return (int) (handle >>> 48);
    }

    private static long decodeOffset(long handle) {
        return handle & 0xFFFFFFFFFFFFL;
    }
}
