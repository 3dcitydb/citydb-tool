/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.store;

import org.citydb.vis.util.FileHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;

/**
 * Owns the lifecycle of the disk-backed stores used during visualization export.
 * <p>
 * Given a pre-allocated temp directory (the caller is responsible for making it
 * unique per run — the CLI controller does so with a {@code .citydb-vis-tmp-*}
 * suffix), this class consolidates store construction, close-time cleanup, and
 * temp-tree deletion so the main writer can focus on the processing pipeline.
 * <p>
 * Stores created eagerly:
 * {@link SpatialEntryStore}, {@link ShardedMeshStore}, {@link AttributeStore}, {@link TextureStore}.
 * <br>
 * {@link NodeEntryStore} is created lazily via {@link #initNodeEntryStore} in the
 * close phase once the estimated node count is known.
 */
public class VisExportStores implements AutoCloseable {
    private final Logger logger = LoggerFactory.getLogger(VisExportStores.class);
    private final Path tempDir;
    private final SpatialEntryStore spatialEntryStore;
    private final ShardedMeshStore meshStore;
    private final AttributeStore attrStore;
    private final TextureStore textureStore;
    private NodeEntryStore nodeEntryStore;

    /**
     * Per-feature bit: 1 if the feature contributed any texture to the atlas.
     * Read by the mixed-node split stage to partition each node's features
     * into textured and untextured subsets (one I3S / 3D Tiles node per
     * subset). Access is synchronized because {@link BitSet} is not
     * concurrency-safe and writes happen from parallel feature-processing
     * threads during the write phase.
     */
    private final BitSet featureTextureFlags = new BitSet();

    /**
     * Create the disk-backed stores inside {@code tempDir}. The directory is
     * expected to already be unique per export run (the CLI controller
     * pre-creates one with a {@code .citydb-vis-tmp-*} suffix so close-time
     * {@code deleteDirectoryTree} can't wipe a sibling export's files).
     */
    public VisExportStores(int cpuCores, Path tempDir) throws IOException {
        Files.createDirectories(tempDir);
        this.tempDir = tempDir;
        this.spatialEntryStore = new SpatialEntryStore(cpuCores, tempDir);
        this.meshStore = new ShardedMeshStore(cpuCores, tempDir);
        this.attrStore = new AttributeStore(tempDir);
        this.textureStore = new TextureStore(tempDir);
    }

    public Path getTempDir() {
        return tempDir;
    }

    public SpatialEntryStore getSpatialEntryStore() {
        return spatialEntryStore;
    }

    public ShardedMeshStore getMeshStore() {
        return meshStore;
    }

    public AttributeStore getAttrStore() {
        return attrStore;
    }

    public TextureStore getTextureStore() {
        return textureStore;
    }

    public NodeEntryStore getNodeEntryStore() {
        return nodeEntryStore;
    }

    public NodeEntryStore initNodeEntryStore(int estimatedNodes) throws IOException {
        nodeEntryStore = new NodeEntryStore(tempDir, estimatedNodes);
        return nodeEntryStore;
    }

    public long entryCount() {
        return spatialEntryStore.entryCount();
    }

    /** Mark the feature as carrying at least one textured polygon. */
    public synchronized void setFeatureTextured(long featureId) {
        featureTextureFlags.set((int) featureId);
    }

    /** Query whether the feature was recorded as textured during the write phase. */
    public synchronized boolean isFeatureTextured(long featureId) {
        return featureTextureFlags.get((int) featureId);
    }

    @Override
    public void close() {
        closeQuietly("node entry store", nodeEntryStore);
        closeQuietly("spatial entry store", spatialEntryStore);
        closeQuietly("mesh store", meshStore);
        closeQuietly("attribute store", attrStore);
        textureStore.close();
        FileHelper.deleteDirectoryTree(tempDir);
    }

    private void closeQuietly(String name, AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception e) {
            logger.warn("Failed to close {}: {}", name, e.getMessage());
        }
    }
}
