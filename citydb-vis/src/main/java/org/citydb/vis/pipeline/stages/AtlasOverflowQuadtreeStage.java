/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.pipeline.stages;

import org.citydb.vis.VisExportException;
import org.citydb.vis.appearance.AtlasOverflowMode;
import org.citydb.vis.appearance.TextureAtlas;
import org.citydb.vis.geometry.TriangleMesh;
import org.citydb.vis.pipeline.PipelineContext;
import org.citydb.vis.pipeline.Stage;
import org.citydb.vis.scene.BoundingVolume;
import org.citydb.vis.scene.SceneNode;
import org.citydb.vis.store.NodeEntry;
import org.citydb.vis.store.NodeEntryStore;
import org.citydb.vis.store.ShardedMeshStore;
import org.citydb.vis.store.TextureStore;
import org.citydb.vis.store.VisExportStores;
import org.citydb.vis.config.VisFormatOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

/**
 * Spatially subdivide every mesh node whose textures would overflow a single
 * {@code maxAtlasSize}² atlas page, recursing 2×2 until each leaf fits or
 * cannot be subdivided further. Intermediate nodes lose their mesh data
 * (push-down split), with new mesh leaves attached as descendants.
 * <p>
 * Runs for {@link AtlasOverflowMode#QUADTREE} (pure split:
 * the cell root becomes a content-less intermediate) and for
 * {@link AtlasOverflowMode#HYBRID} (split plus a
 * low-resolution rescaled preview retained on each split cell root,
 * marked via {@link SceneNode#setLodPreview(boolean)} for the writer to
 * emit). The fallback paths and per-leaf packing are identical between the
 * two modes; only the cell-root retention differs.
 * <p>
 * Replaces the silent global texture rescale that {@link TextureAtlas#build}
 * otherwise applies inside the writer's {@code prepareNodeMesh} step. Each
 * resulting leaf packs its self-contained subset of the parent's textures into
 * a single page at full resolution. Two fallbacks keep recursion bounded:
 * <ol>
 *   <li>A node containing a single feature whose textures still overflow
 *       cannot be subdivided spatially — accept the rescale path at that
 *       leaf.</li>
 *   <li>The recursion is depth-capped at {@value #MAX_DEPTH} levels from any
 *       node observed when the stage starts (so a cell root pushed down by
 *       {@link MixedNodeSplitStage} can still subdivide that many extra
 *       levels). Beyond the cap, accept the rescale fallback.</li>
 * </ol>
 * <p>
 * Centroid binning routes each feature to the quadrant containing its 2D
 * (longitude, latitude) AABB centroid, computed once per node from the
 * merged triangle mesh. Z extent of each quadrant inherits the parent's Z
 * range — runtime culling tightness is not the goal here.
 * <p>
 * Run after {@link MixedNodeSplitStage}: that stage guarantees every mesh
 * node is feature-level homogeneous (all features either textured or all
 * untextured), so a single check on the first entry's flag is enough to skip
 * untextured nodes without loading their geometry.
 * <p>
 * Cell-root subtrees are independent of each other (no shared
 * {@link SceneNode} ancestors below {@code globalRoot}, no shared per-feature
 * meshes), so the per-cell BFS runs in a thread pool. Workers create children
 * with placeholder index {@code -1} and accumulate per-tree plans. The main
 * thread then assigns sequential indices, registers nodes in {@code allNodes},
 * and writes {@link NodeEntry} buckets — keeping {@link NodeEntryStore} and
 * {@code allNodes} writes serial (the store's writes are not thread-safe and
 * the deterministic index sequence keeps tile/file naming reproducible).
 */
public final class AtlasOverflowQuadtreeStage implements Stage {
    private static final Logger logger = LoggerFactory.getLogger(AtlasOverflowQuadtreeStage.class);
    private static final int MAX_DEPTH = 8;

    /**
     * Per-cell-tree work product produced by a worker. Carries the planned
     * children (placeholder index = -1) and their {@link NodeEntry} buckets,
     * plus stats. The main thread later assigns sequential indices, registers
     * nodes in {@code allNodes}, and writes the buckets.
     */
    private static final class CellTreePlan {
        final int rootIndex;
        // Created SceneNodes in BFS order. Each carries placeholder index -1
        // until the main thread assigns the final index.
        final List<SceneNode> created = new ArrayList<>();
        // Bucket per planned node, parallel-indexed with `created`.
        final List<List<NodeEntry>> buckets = new ArrayList<>();
        boolean rootBecameIntermediate = false;
        int evaluated = 0;
        int splitCount = 0;
        int maxDepthReached = 0;
        int singleFeatureFallback = 0;
        int depthCapFallback = 0;

        CellTreePlan(int rootIndex) {
            this.rootIndex = rootIndex;
        }
    }

    @Override
    public void execute(PipelineContext ctx) throws VisExportException {
        VisFormatOptions opts = ctx.formatOptions();
        AtlasOverflowMode mode = opts.getAtlasOverflowMode();
        if (mode != AtlasOverflowMode.QUADTREE
                && mode != AtlasOverflowMode.HYBRID) {
            return;
        }
        if (!ctx.hasTextures()) {
            return;
        }
        boolean withPreview = mode == AtlasOverflowMode.HYBRID;

        VisExportStores stores = ctx.stores();
        NodeEntryStore nodeEntryStore = stores.getNodeEntryStore();
        TextureStore textureStore = stores.getTextureStore();
        ShardedMeshStore meshStore = stores.getMeshStore();
        List<SceneNode> allNodes = ctx.allNodes();
        Set<Integer> meshNodeIndices = ctx.meshNodeIndices();

        double textureScale = opts.getTextureScale();
        int maxAtlasSize = opts.getMaxAtlasSize();

        List<Integer> initialRoots = new ArrayList<>(meshNodeIndices);

        // Parallel worker pass: each cell-root subtree is processed end-to-end
        // by one worker. Workers only mutate their own cell-root subtree and
        // local plan; they read shared stores via thread-safe positional reads.
        int parallelism = Math.max(2, Runtime.getRuntime().availableProcessors());
        ForkJoinPool pool = new ForkJoinPool(parallelism);
        List<CellTreePlan> plans;
        try {
            plans = pool.submit(() -> initialRoots.parallelStream()
                    .map(rootIdx -> {
                        try {
                            return processCellTree(rootIdx, allNodes, nodeEntryStore,
                                    meshStore, textureStore, stores,
                                    textureScale, maxAtlasSize);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .toList()
            ).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VisExportException("Interrupted while subdividing overflowing nodes.", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof UncheckedIOException ioe) {
                throw new VisExportException("Failed to subdivide overflowing nodes.", ioe.getCause());
            }
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new VisExportException("Failed to subdivide overflowing nodes.", cause);
        } finally {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(1, TimeUnit.MINUTES)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException e) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // Sequential commit pass: assign indices, register in allNodes, write
        // NodeEntry buckets. This must run on a single thread because
        // NodeEntryStore.writeNode is not thread-safe and we want deterministic
        // index assignment for reproducible tile naming.
        Set<Integer> updatedMeshIndices = new HashSet<>(meshNodeIndices);
        int nextIndex = allNodes.size();
        int evaluated = 0;
        int splitCount = 0;
        int maxDepthReached = 0;
        int singleFeatureFallback = 0;
        int depthCapFallback = 0;

        try {
            for (CellTreePlan plan : plans) {
                evaluated += plan.evaluated;
                splitCount += plan.splitCount;
                maxDepthReached = Math.max(maxDepthReached, plan.maxDepthReached);
                singleFeatureFallback += plan.singleFeatureFallback;
                depthCapFallback += plan.depthCapFallback;

                if (plan.rootBecameIntermediate) {
                    if (withPreview) {
                        // HYBRID: keep the split root in meshNodeIndices and
                        // mark it as an LOD preview. The writer will emit a
                        // low-resolution single-atlas (RESCALE strategy) for
                        // fast distant view, replaced at runtime by the
                        // quadtree-leaf children once they cross the LOD
                        // threshold. For 3D Tiles the serializer emits
                        // refine=REPLACE on this tile so children replace it
                        // once loaded; I3S handles refinement automatically
                        // via uniform lodThreshold.
                        SceneNode root = allNodes.get(plan.rootIndex);
                        root.setLodPreview(true);
                        // updatedMeshIndices already contains plan.rootIndex
                        // from the initial copy; intentionally not removed.
                    } else {
                        // QUADTREE (pure split): the split root becomes a
                        // content-less intermediate. The runtime refines from
                        // the cell's parent aggregation directly to the
                        // quadtree leaves, with no extra preview level. Drop
                        // the root from meshNodeIndices so the writer skips
                        // geometry/atlas emission for it; its on-disk
                        // NodeEntry list is orphaned (same trade-off as
                        // MixedNodeSplitStage).
                        updatedMeshIndices.remove(plan.rootIndex);
                    }
                }

                for (int i = 0; i < plan.created.size(); i++) {
                    SceneNode planned = plan.created.get(i);
                    List<NodeEntry> bucket = plan.buckets.get(i);
                    planned.setIndex(nextIndex);
                    allNodes.add(planned);
                    if (planned.getChildren().isEmpty()) {
                        // Leaf — its bucket needs to be loadable later by the
                        // writer. Intermediate planned nodes were further split
                        // into their own children and their NodeEntries, like
                        // the original cell root's, are simply not written
                        // (orphaned, same trade-off as MixedNodeSplitStage).
                        nodeEntryStore.writeNode(nextIndex, bucket);
                        updatedMeshIndices.add(nextIndex);
                    }
                    nextIndex++;
                }
            }
        } catch (IOException e) {
            throw new VisExportException("Failed to commit subdivision plan.", e);
        }

        if (splitCount > 0 || singleFeatureFallback > 0 || depthCapFallback > 0) {
            ctx.setMeshNodeIndices(updatedMeshIndices);
            // Stage is format-agnostic: the per-format overflow path on
            // fallback nodes depends on the writer's AtlasMode and the user's
            // --atlas-fallback choice. I3S always uses SINGLE_ATLAS (rescale
            // and/or atlas-size expansion); 3D Tiles uses AUTO (multi-page
            // atlas, no quality loss) when --atlas-fallback=expand and
            // SINGLE_ATLAS (rescale, matching I3S) when --atlas-fallback=
            // rescale. This stage just records that subdivision could not
            // resolve the overflow further.
            logger.info("Atlas overflow split: evaluated {} mesh node(s), split {}, " +
                            "max depth reached {}, single-feature fallback(s) {}, " +
                            "depth-cap fallback(s) {}.",
                    evaluated, splitCount, maxDepthReached,
                    singleFeatureFallback, depthCapFallback);
            if (depthCapFallback > 0) {
                logger.warn("{} node(s) hit the {}-level depth cap and will fall back " +
                                "to the writer's per-format atlas-overflow path. " +
                                "Consider raising --max-atlas-size or lowering " +
                                "--texture-scale to reduce this count.",
                        depthCapFallback, MAX_DEPTH);
            }
        }
    }

    /**
     * Worker: process one cell-root subtree end-to-end, producing a plan with
     * placeholder children. Runs on a {@link ForkJoinPool} thread; only mutates
     * the cell-root subtree's {@link SceneNode} structure (via {@code addChild})
     * and the worker-local plan and mesh cache.
     */
    private static CellTreePlan processCellTree(int rootIdx,
                                                List<SceneNode> allNodes,
                                                NodeEntryStore nodeEntryStore,
                                                ShardedMeshStore meshStore,
                                                TextureStore textureStore,
                                                VisExportStores stores,
                                                double textureScale,
                                                int maxAtlasSize) throws IOException {
        CellTreePlan plan = new CellTreePlan(rootIdx);
        SceneNode root = allNodes.get(rootIdx);

        // Per-feature mesh cache scoped to this cell-root subtree. Parent and
        // children share most features after a split; caching by meshHandle
        // turns the depth-multiplier into a constant. Cleared automatically
        // when this method returns and the local map goes out of scope.
        Map<Long, TriangleMesh> meshCache = new HashMap<>();

        // Inner BFS work item: (parent SceneNode, NodeEntries owned by this
        // node, depth from cell root). Parent reference is either the cell
        // root or a previously-created planned child.
        Deque<WorkItem> queue = new ArrayDeque<>();
        queue.add(new WorkItem(root, nodeEntryStore.loadNode(rootIdx), 0));

        while (!queue.isEmpty()) {
            WorkItem item = queue.poll();
            SceneNode current = item.node;
            List<NodeEntry> entries = item.entries;
            int depth = item.depth;
            plan.evaluated++;

            if (entries.isEmpty()) continue;

            // After MixedNodeSplitStage every mesh node is feature-level
            // homogeneous textured or untextured. A single flag check on any
            // entry tells us whether to skip the geometry load.
            if (!stores.isFeatureTextured(entries.get(0).id())) continue;

            TriangleMesh merged = new TriangleMesh();
            for (NodeEntry entry : entries) {
                merged.merge(loadCachedMesh(meshStore, meshCache, entry.meshHandle()));
            }

            Set<Integer> texIds = new LinkedHashSet<>();
            for (int texId : merged.getTriangleTextureIds()) {
                if (texId >= 0) texIds.add(texId);
            }
            if (texIds.isEmpty()) continue;

            Map<Integer, float[]> uvExtents = merged.computeUVExtents();
            if (!TextureAtlas.wouldOverflow(texIds, textureStore,
                    textureScale, maxAtlasSize, uvExtents)) {
                continue;
            }

            if (entries.size() == 1) {
                plan.singleFeatureFallback++;
                continue;
            }
            if (depth >= MAX_DEPTH) {
                plan.depthCapFallback++;
                continue;
            }

            BoundingVolume bbox = current.getBoundingVolume();
            double midX = (bbox.getMinX() + bbox.getMaxX()) * 0.5;
            double midY = (bbox.getMinY() + bbox.getMaxY()) * 0.5;

            Map<Long, double[]> featureBounds = computeFeatureBounds(merged);

            List<List<NodeEntry>> buckets = new ArrayList<>(4);
            for (int q = 0; q < 4; q++) {
                buckets.add(new ArrayList<>());
            }
            for (NodeEntry entry : entries) {
                double[] fb = featureBounds.get(entry.id());
                double cx, cy;
                if (fb == null) {
                    // Feature contributed no triangles to the merged mesh
                    // (e.g., empty geometry after triangulation). Keep the
                    // entry alive by routing to the parent's center; no
                    // visible geometry is associated with it.
                    cx = midX;
                    cy = midY;
                } else {
                    cx = (fb[0] + fb[2]) * 0.5;
                    cy = (fb[1] + fb[3]) * 0.5;
                }
                int qx = cx > midX ? 1 : 0;
                int qy = cy > midY ? 1 : 0;
                buckets.get(qx + qy * 2).add(entry);
            }

            int nonEmpty = 0;
            for (List<NodeEntry> bucket : buckets) {
                if (!bucket.isEmpty()) nonEmpty++;
            }
            // All centroids cluster into one quadrant (degenerate bbox or
            // tightly-packed features). Spatial subdivision would not separate
            // them, so accept the rescale fallback at this leaf.
            if (nonEmpty <= 1) {
                plan.singleFeatureFallback++;
                continue;
            }

            int childLevel = current.getLevel() + 1;
            int childDepth = depth + 1;
            for (int q = 0; q < 4; q++) {
                List<NodeEntry> bucket = buckets.get(q);
                if (bucket.isEmpty()) continue;

                int qx = q & 1;
                int qy = (q >> 1) & 1;
                double cMinX = qx == 0 ? bbox.getMinX() : midX;
                double cMaxX = qx == 0 ? midX : bbox.getMaxX();
                double cMinY = qy == 0 ? bbox.getMinY() : midY;
                double cMaxY = qy == 0 ? midY : bbox.getMaxY();
                BoundingVolume childBbox = BoundingVolume.ofBoundingBox(
                        cMinX, cMinY, bbox.getMinZ(),
                        cMaxX, cMaxY, bbox.getMaxZ());

                // Placeholder index -1; the main thread assigns the real
                // value when committing the plan.
                SceneNode child = new SceneNode(-1, childLevel);
                child.setBoundingVolume(childBbox);
                child.setFeatureCount(bucket.size());
                current.addChild(child);

                plan.created.add(child);
                plan.buckets.add(bucket);
                queue.add(new WorkItem(child, bucket, childDepth));
            }

            if (current == root) {
                plan.rootBecameIntermediate = true;
            }
            plan.splitCount++;
            if (childDepth > plan.maxDepthReached) {
                plan.maxDepthReached = childDepth;
            }
        }

        return plan;
    }

    /** Inner BFS work item. */
    private record WorkItem(SceneNode node, List<NodeEntry> entries, int depth) {
    }

    /**
     * Load a per-feature {@link TriangleMesh}, hitting the cache first so that
     * a feature appearing in both a parent and one of its quadtree children
     * (always the case after a split) reads from disk only once. Cache is
     * scoped to one cell-root subtree (one worker), so peak memory is bounded
     * by that subtree's per-feature mesh footprint.
     * <p>
     * Cached meshes are read-only — {@link TriangleMesh#merge} only appends
     * references to the destination, so a single cached source is safe to
     * pass to multiple {@code merge} calls.
     */
    private static TriangleMesh loadCachedMesh(ShardedMeshStore store,
                                               Map<Long, TriangleMesh> cache,
                                               long meshHandle) throws IOException {
        TriangleMesh cached = cache.get(meshHandle);
        if (cached != null) return cached;
        TriangleMesh fresh = store.load(meshHandle);
        cache.put(meshHandle, fresh);
        return fresh;
    }

    /**
     * Per-feature 2D AABB {@code [minX, minY, maxX, maxY]} from the merged
     * triangle mesh. Each triangle's vertices are expanded into the bbox of
     * its feature id; Z is irrelevant for quadrant assignment so it's not
     * tracked.
     */
    private static Map<Long, double[]> computeFeatureBounds(TriangleMesh mesh) {
        Map<Long, double[]> bounds = new HashMap<>();
        List<int[]> tris = mesh.getTriangles();
        List<Long> fids = mesh.getFeatureIds();
        List<double[]> positions = mesh.getPositions();
        for (int t = 0; t < tris.size(); t++) {
            long fid = fids.get(t);
            double[] b = bounds.computeIfAbsent(fid, k -> new double[]{
                    Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                    Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY});
            for (int vi : tris.get(t)) {
                double[] p = positions.get(vi);
                if (p[0] < b[0]) b[0] = p[0];
                if (p[1] < b[1]) b[1] = p[1];
                if (p[0] > b[2]) b[2] = p[0];
                if (p[1] > b[3]) b[3] = p[1];
            }
        }
        return bounds;
    }
}
