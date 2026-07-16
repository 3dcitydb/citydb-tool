/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.writer;

import org.citydb.vis.VisExportException;
import org.citydb.vis.appearance.AtlasFallbackStrategy;
import org.citydb.vis.appearance.AtlasMode;
import org.citydb.vis.appearance.TextureAtlas;
import org.citydb.vis.appearance.TextureAtlasBuilder;
import org.citydb.vis.config.VisFormatOptions;
import org.citydb.vis.geometry.TriangleMesh;
import org.citydb.vis.model.FeatureData;
import org.citydb.vis.scene.SceneNode;
import org.citydb.vis.store.AttributeStore;
import org.citydb.vis.store.NodeEntry;
import org.citydb.vis.store.VisExportStores;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Assembles a complete scene node from the disk-backed shard stores for the
 * close-phase output, shared by the format-specific writers ({@code I3SWriter},
 * {@code Tiles3DWriter}).
 * <p>
 * For each node it merges the sharded meshes, builds the texture atlas(es),
 * remaps UVs, and loads the per-feature attributes; it also drives the parallel
 * node fan-out. The format-specific encoding step is supplied by the caller as
 * a {@link NodeBody} and run inside {@link #writeNode}'s uniform
 * failure-wrapping.
 * <p>
 * Extracted from {@code VisWriter} so the ingestion/lifecycle base and the
 * output-phase toolkit each carry a single responsibility; a {@code VisWriter}
 * hands its subclasses one instance via {@code nodeAssembler()}.
 * <p>
 * Stateless beyond its injected collaborators and safe for the concurrent
 * per-node calls issued by {@link #processNodesParallel}.
 */
public final class NodeAssembler {
    private final Logger logger = LoggerFactory.getLogger(NodeAssembler.class);
    private final VisExportStores stores;
    private final VisFormatOptions formatOptions;
    private final int numberOfThreads;
    // Whether the writer's geometry encoder samples the atlas for untextured
    // triangles and therefore needs the single-atlas build to reserve a 4×4
    // white-pixel sentinel for intra-feature mixed cells. See VisWriter's
    // atlasNeedsWhitePixelSentinel().
    private final boolean atlasNeedsWhitePixelSentinel;

    NodeAssembler(VisExportStores stores, VisFormatOptions formatOptions,
                  int numberOfThreads, boolean atlasNeedsWhitePixelSentinel) {
        this.stores = stores;
        this.formatOptions = formatOptions;
        this.numberOfThreads = numberOfThreads;
        this.atlasNeedsWhitePixelSentinel = atlasNeedsWhitePixelSentinel;
    }

    /**
     * Prepared per-node data: merged mesh with atlas-remapped UVs.
     * <p>
     * {@code atlases} holds one entry in the single-atlas case (I3S — required
     * by the spec's one-material-per-node rule) and one-or-more in the
     * multi-atlas case (3D Tiles, where GLB supports multiple materials per
     * mesh). Empty when the node carries no textured triangles or every
     * referenced texture failed to load.
     */
    public record PreparedNode(List<NodeEntry> entries, TriangleMesh mesh,
                               List<TextureAtlas> atlases) {
    }

    /**
     * Prepare a node's mesh data: load and merge meshes from the sharded
     * store, build texture atlas(es), and remap UV coordinates.
     */
    private PreparedNode prepareNodeMesh(SceneNode node, AtlasMode mode) throws VisExportException {
        try {
            List<NodeEntry> entries = stores.getNodeEntryStore().loadNode(node.getIndex());

            TriangleMesh merged = new TriangleMesh();
            for (NodeEntry entry : entries) {
                merged.merge(stores.getMeshStore().load(entry.meshHandle()));
            }

            Set<Integer> uniqueTexIds = new LinkedHashSet<>();
            boolean hasUntexturedTriangle = false;
            for (int texId : merged.getTriangleTextureIds()) {
                if (texId >= 0) uniqueTexIds.add(texId);
                else hasUntexturedTriangle = true;
            }
            if (uniqueTexIds.isEmpty() && merged.hasTexCoords()) {
                merged.setHasTexCoords(false);
            }

            List<TextureAtlas> atlases = List.of();
            if (!uniqueTexIds.isEmpty()) {
                Map<Integer, float[]> uvExtents = merged.computeUVExtents();
                // LOD-preview nodes are split-cell parents that need a
                // low-resolution single-atlas preview (replaced at runtime
                // by the high-resolution split children). Force
                // single-atlas + RESCALE strategy regardless of the user's
                // --atlas-fallback so the preview always fits one page and
                // stays within the user's --max-atlas-size budget.
                boolean lodPreview = node.isLodPreview();
                // AUTO: prefer single page; spill to multi-page only when
                // the BSP packer would otherwise overflow. The wouldOverflow
                // predicate runs on metadata only (~3-4% of full build cost),
                // so the extra check is negligible and keeps the common case
                // on the cheaper single-atlas path.
                boolean useMulti = !lodPreview
                        && mode == AtlasMode.AUTO
                        && TextureAtlasBuilder.wouldOverflow(uniqueTexIds, stores.getTextureStore(),
                                formatOptions.getTextureScale(),
                                formatOptions.getMaxAtlasSize(), uvExtents);
                if (useMulti) {
                    atlases = TextureAtlasBuilder.buildMulti(
                            uniqueTexIds, stores.getTextureStore(), formatOptions.getTextureScale(),
                            formatOptions.getMaxAtlasSize(), uvExtents);
                } else {
                    // Only reserve the 4x4 white-pixel sentinel when the
                    // writer's encoder actually samples the atlas for
                    // untextured triangles (I3S, single material per node).
                    // 3D Tiles partitions untextured triangles to a separate
                    // primitive in the GLB and never samples the atlas for
                    // them, so reserving the sentinel just wastes BSP space
                    // and can push a borderline-fitting atlas over the edge,
                    // triggering needless Phase 2 expansion.
                    boolean needsWhitePixel = hasUntexturedTriangle && atlasNeedsWhitePixelSentinel;
                    AtlasFallbackStrategy strategy = lodPreview
                            ? AtlasFallbackStrategy.RESCALE
                            : formatOptions.getAtlasFallbackStrategy();
                    TextureAtlas single = TextureAtlasBuilder.build(
                            uniqueTexIds, stores.getTextureStore(), formatOptions.getTextureScale(),
                            formatOptions.getMaxAtlasSize(), uvExtents, needsWhitePixel,
                            strategy);
                    atlases = single != null ? List.of(single) : List.of();
                }
                if (!atlases.isEmpty()) {
                    for (TextureAtlas atlas : atlases) {
                        atlas.remapUVs(merged);
                    }
                } else {
                    logger.warn("Node {}: all referenced textures failed to load, " +
                            "falling back to untextured rendering.", node.getIndex());
                    merged.setHasTexCoords(false);
                }
            }
            node.setTextured(!atlases.isEmpty());

            return new PreparedNode(entries, merged, atlases);
        } catch (IOException e) {
            throw new VisExportException("Failed to prepare node " + node.getIndex() + ".", e);
        }
    }

    /**
     * Load per-feature attribute data from the disk-backed attribute store.
     */
    private List<FeatureData> loadNodeFeatures(List<NodeEntry> entries) throws VisExportException {
        try {
            List<FeatureData> features = new ArrayList<>(entries.size());
            for (NodeEntry entry : entries) {
                AttributeStore.FeatureAttrs attrs = stores.getAttrStore().load(entry.attrOffset());
                features.add(new FeatureData(
                        entry.id(), attrs.objectId(), attrs.featureType(),
                        attrs.attributes()));
            }
            return features;
        } catch (IOException e) {
            throw new VisExportException("Failed to load node attribute data.", e);
        }
    }

    /**
     * Format-specific body of a per-node write, run by {@link #writeNode} after
     * the shared prepare/load/log steps and inside its uniform
     * {@link IOException}→{@link VisExportException} wrapper.
     *
     * @return {@code false} if the node yielded no writable geometry and should
     *         be dropped (mirrors the encoders' {@code null} result)
     */
    @FunctionalInterface
    public interface NodeBody {
        boolean write(PreparedNode prepared, List<FeatureData> features) throws IOException;
    }

    /**
     * Shared per-node write scaffold for both output formats: prepare the node
     * mesh + atlas(es), load its feature attributes, log any atlas-budget
     * violations, then run the format-specific {@code body} under one
     * {@code IOException} wrapper. Keeps the prepare/load/log sequence and the
     * failure-message shape identical across the I3S and 3D Tiles writers.
     */
    public boolean writeNode(SceneNode node, AtlasMode mode, String formatName,
                             NodeBody body) throws VisExportException {
        PreparedNode prepared = prepareNodeMesh(node, mode);
        List<FeatureData> features = loadNodeFeatures(prepared.entries());
        logAtlasViolations(node, prepared, features);
        try {
            return body.write(prepared, features);
        } catch (IOException e) {
            throw new VisExportException(
                    "Failed to write " + formatName + " node " + node.getIndex() + ".", e);
        }
    }

    /**
     * Emit a DEBUG per detected user-setting violation on the prepared node:
     * <ul>
     *   <li>Atlas page size exceeded {@code --max-atlas-size} — fires when the
     *       single-atlas path's atlas-size expansion grew the page beyond the
     *       user-requested cap (typical I3S {@code --atlas-fallback=expand}
     *       residual case). 3D Tiles {@code AUTO} mode does not trigger this
     *       since it spills to multi-page atlases instead.</li>
     *   <li>Texture scale dropped below {@code --texture-scale} — fires when
     *       the rescale loop shrank textures uniformly (typical
     *       {@code --atlas-fallback=rescale} residual case, or the EXPAND
     *       last-resort post-expansion rescale at 16K).</li>
     * </ul>
     * DEBUG (not WARN): residual cells in the default {@code expand} mode are
     * common — hundreds per export — and the violations are by-design behavior
     * of the chosen fallback strategy, not errors. Users debugging a specific
     * building's missing/blurry texture can enable DEBUG to see the per-cell
     * detail naming the affected {@code gml:id}.
     */
    private void logAtlasViolations(SceneNode node, PreparedNode prepared,
                                    List<FeatureData> features) {
        if (prepared.atlases().isEmpty() || !logger.isDebugEnabled()) {
            return;
        }

        int requestedMaxSize = formatOptions.getMaxAtlasSize();
        double requestedScale = formatOptions.getTextureScale();

        int actualMaxSize = 0;
        double actualScale = 1.0;
        for (TextureAtlas atlas : prepared.atlases()) {
            actualMaxSize = Math.max(actualMaxSize, Math.max(atlas.getWidth(), atlas.getHeight()));
            actualScale = Math.min(actualScale, atlas.getActualScale());
        }

        boolean atlasExpanded = actualMaxSize > requestedMaxSize;
        boolean texturesRescaled = actualScale < requestedScale;
        if (!atlasExpanded && !texturesRescaled) {
            return;
        }

        String featureSummary = formatFeatureSummary(features);
        if (atlasExpanded) {
            logger.debug("Atlas page expanded to {}×{} beyond --max-atlas-size {}×{} " +
                            "for {} (node {}).",
                    actualMaxSize, actualMaxSize, requestedMaxSize, requestedMaxSize,
                    featureSummary, node.getIndex());
        }
        if (texturesRescaled) {
            logger.debug("Textures rescaled from --texture-scale {} to {} for {} " +
                            "(node {}).",
                    requestedScale, actualScale, featureSummary, node.getIndex());
        }
    }

    /**
     * Render a human-readable feature list for a violation DEBUG message.
     * Single feature shows the bare {@code gml:id}; up to five list all of
     * them; larger nodes show count + first id as a sample.
     */
    private static String formatFeatureSummary(List<FeatureData> features) {
        if (features.isEmpty()) {
            return "node with no features";
        }
        if (features.size() == 1) {
            return "feature " + features.get(0).objectId();
        }
        if (features.size() <= 5) {
            StringBuilder sb = new StringBuilder("features ");
            for (int i = 0; i < features.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(features.get(i).objectId());
            }
            return sb.toString();
        }
        return features.size() + " features (first: " + features.get(0).objectId() + ")";
    }

    // ---- Parallel node processing -------------------------------------------

    @FunctionalInterface
    public interface NodeProcessor {
        boolean process(SceneNode node) throws VisExportException;
    }

    /**
     * Process mesh nodes in parallel using a ForkJoinPool, track progress,
     * and strip nodes whose geometry was empty after welding/degenerate
     * filtering.
     *
     * @return effective mesh node indices (input set minus empty nodes)
     */
    public Set<Integer> processNodesParallel(List<SceneNode> allNodes,
                                             Set<Integer> meshNodeIndices,
                                             NodeProcessor processor) throws VisExportException {
        List<SceneNode> meshNodes = allNodes.stream()
                .filter(n -> meshNodeIndices.contains(n.getIndex()))
                .toList();

        Set<Integer> emptyNodeIndices = ConcurrentHashMap.newKeySet();
        AtomicInteger nodesProcessed = new AtomicInteger(0);
        int total = meshNodes.size();
        ForkJoinPool pool = new ForkJoinPool(numberOfThreads);
        try {
            pool.submit(() -> meshNodes.parallelStream().forEach(node -> {
                try {
                    if (!processor.process(node)) {
                        emptyNodeIndices.add(node.getIndex());
                    }
                } catch (VisExportException e) {
                    throw new NodeProcessingException(e);
                }
                int done = nodesProcessed.incrementAndGet();
                if (done % 100 == 0 || done == total) {
                    logger.info("Nodes written: {}/{}.", done, total);
                }
            })).join();
        } catch (NodeProcessingException e) {
            throw e.cause;
        } finally {
            pool.shutdown();
            try {
                pool.awaitTermination(1, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        Set<Integer> result = meshNodeIndices;
        if (!emptyNodeIndices.isEmpty()) {
            result = new HashSet<>(meshNodeIndices);
            result.removeAll(emptyNodeIndices);
            logger.info("Dropped {} empty mesh nodes (all triangles degenerate after welding).",
                    emptyNodeIndices.size());
        }
        return result;
    }

    /** Unchecked wrapper to tunnel {@link VisExportException} through parallel streams. */
    private static final class NodeProcessingException extends RuntimeException {
        private final VisExportException cause;

        NodeProcessingException(VisExportException cause) {
            super(cause);
            this.cause = cause;
        }
    }
}
