/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.pipeline.stages;

import org.citydb.model.common.Name;
import org.citydb.vis.VisExportException;
import org.citydb.vis.appearance.AtlasOverflowMode;
import org.citydb.vis.attribute.AttributeEncoder;
import org.citydb.vis.options.Tiles3DFormatOptions;
import org.citydb.vis.geometry.TriangleMesh;
import org.citydb.vis.pipeline.PipelineContext;
import org.citydb.vis.scene.BoundingVolume;
import org.citydb.vis.scene.SceneNode;
import org.citydb.vis.store.NodeEntry;
import org.citydb.vis.store.VisExportStores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterizes the atlas-overflow subdivision: a textured cell whose sources
 * cannot pack into one {@code maxAtlasSize}² page is split 2×2 by feature
 * centroid, the root becomes an intermediate (SPLIT) or an LOD preview
 * (HYBRID), and the fallback paths (single feature, no overflow, FLAT mode)
 * leave the tree untouched. Four 1024² textures against the 1024 minimum
 * atlas size guarantee overflow without large fixture images.
 * <p>
 * The scene is a unit-degree cell with one single-triangle feature per
 * quadrant, so the expected child bounding volumes are the exact quadrant
 * boxes of the parent.
 */
class AtlasOverflowSplitStageTest {
    private static final Name SURFACE = Name.of("WallSurface", "ns");
    private static final BoundingVolume CELL_BBOX =
            BoundingVolume.ofBoundingBox(0.0, 0.0, 0.0, 1.0, 1.0, 10.0);
    // Feature id → quadrant centroid, one feature per quadrant.
    private static final double[][] CENTROIDS = {
            {0.25, 0.25}, {0.75, 0.25}, {0.25, 0.75}, {0.75, 0.75}};

    @Test
    void splitModeSubdividesOverflowingCellByQuadrant(@TempDir Path tempDir)
            throws IOException, VisExportException {
        try (VisExportStores stores = new VisExportStores(2, tempDir.resolve("stores"))) {
            PipelineContext ctx = overflowingCell(stores, AtlasOverflowMode.SPLIT);
            new AtlasOverflowSplitStage().execute(ctx);

            List<SceneNode> allNodes = ctx.allNodes();
            assertEquals(6, allNodes.size());
            for (int i = 0; i < allNodes.size(); i++) {
                assertEquals(i, allNodes.get(i).getIndex());
            }

            // Pure split: the cell root loses its mesh role entirely.
            assertEquals(Set.of(2, 3, 4, 5), ctx.meshNodeIndices());
            SceneNode cellRoot = allNodes.get(1);
            assertEquals(4, cellRoot.getChildren().size());
            assertFalse(cellRoot.isLodPreview());

            // Each child holds exactly the feature whose centroid falls in its
            // quadrant, and its bbox is that exact quadrant of the parent.
            for (int childIndex = 2; childIndex <= 5; childIndex++) {
                List<NodeEntry> entries = stores.getNodeEntryStore().loadNode(childIndex);
                assertEquals(1, entries.size());
                SceneNode child = allNodes.get(childIndex);
                assertEquals(cellRoot.getLevel() + 1, child.getLevel());
                assertEquals(1, child.getFeatureCount());

                double[] centroid = CENTROIDS[(int) entries.get(0).id() - 1];
                BoundingVolume bbox = child.getBoundingVolume();
                assertEquals(centroid[0] < 0.5 ? 0.0 : 0.5, bbox.getMinX(), 1e-9);
                assertEquals(centroid[0] < 0.5 ? 0.5 : 1.0, bbox.getMaxX(), 1e-9);
                assertEquals(centroid[1] < 0.5 ? 0.0 : 0.5, bbox.getMinY(), 1e-9);
                assertEquals(centroid[1] < 0.5 ? 0.5 : 1.0, bbox.getMaxY(), 1e-9);
                // Z range is inherited from the parent, not tightened.
                assertEquals(CELL_BBOX.getMinZ(), bbox.getMinZ(), 1e-9);
                assertEquals(CELL_BBOX.getMaxZ(), bbox.getMaxZ(), 1e-9);
            }
        }
    }

    @Test
    void twoOverflowingCellsCommitInRootOrderWithPerTreeIsolation(@TempDir Path tempDir)
            throws IOException, VisExportException {
        try (VisExportStores stores = new VisExportStores(2, tempDir.resolve("stores"))) {
            PipelineContext ctx = twoOverflowingCells(stores, AtlasOverflowMode.SPLIT);
            new AtlasOverflowSplitStage().execute(ctx);

            // globalRoot + 2 cell roots + 4 split leaves per cell.
            List<SceneNode> allNodes = ctx.allNodes();
            assertEquals(11, allNodes.size());
            for (int i = 0; i < allNodes.size(); i++) {
                assertEquals(i, allNodes.get(i).getIndex());
            }

            // Both roots turn intermediate; only the eight split leaves remain.
            assertEquals(Set.of(3, 4, 5, 6, 7, 8, 9, 10), ctx.meshNodeIndices());

            // Plans commit in initialRoots order regardless of which worker
            // finished first: cell 1's children occupy the first index block,
            // cell 2's the second, and each child references only a feature
            // ingested into its own cell.
            assertCellTreeSplit(stores, allNodes.get(1),
                    Set.of(3, 4, 5, 6), Set.of(1L, 2L, 3L, 4L));
            assertCellTreeSplit(stores, allNodes.get(2),
                    Set.of(7, 8, 9, 10), Set.of(5L, 6L, 7L, 8L));
        }
    }

    @Test
    void hybridModeKeepsRootAsLodPreview(@TempDir Path tempDir)
            throws IOException, VisExportException {
        try (VisExportStores stores = new VisExportStores(2, tempDir.resolve("stores"))) {
            PipelineContext ctx = overflowingCell(stores, AtlasOverflowMode.HYBRID);
            new AtlasOverflowSplitStage().execute(ctx);

            // Root stays a mesh node (low-res preview) alongside its children.
            assertEquals(Set.of(1, 2, 3, 4, 5), ctx.meshNodeIndices());
            SceneNode cellRoot = ctx.allNodes().get(1);
            assertTrue(cellRoot.isLodPreview());
            assertEquals(4, cellRoot.getChildren().size());
        }
    }

    @Test
    void flatModeIsANoOp(@TempDir Path tempDir) throws IOException, VisExportException {
        try (VisExportStores stores = new VisExportStores(2, tempDir.resolve("stores"))) {
            PipelineContext ctx = overflowingCell(stores, AtlasOverflowMode.FLAT);
            new AtlasOverflowSplitStage().execute(ctx);

            assertEquals(Set.of(1), ctx.meshNodeIndices());
            assertEquals(2, ctx.allNodes().size());
            assertTrue(ctx.allNodes().get(1).getChildren().isEmpty());
        }
    }

    @Test
    void nonOverflowingCellIsLeftAlone(@TempDir Path tempDir)
            throws IOException, VisExportException {
        try (VisExportStores stores = new VisExportStores(2, tempDir.resolve("stores"))) {
            // Four tiny textures pack comfortably into one 1024² page.
            PipelineContext ctx = cell(stores, AtlasOverflowMode.SPLIT, 64);
            new AtlasOverflowSplitStage().execute(ctx);

            assertEquals(Set.of(1), ctx.meshNodeIndices());
            assertEquals(2, ctx.allNodes().size());
            assertTrue(ctx.allNodes().get(1).getChildren().isEmpty());
        }
    }

    @Test
    void singleFeatureOverflowFallsBackWithoutSplit(@TempDir Path tempDir)
            throws IOException, VisExportException {
        try (VisExportStores stores = new VisExportStores(2, tempDir.resolve("stores"))) {
            Path storeDir = stores.getTempDir();
            writePng(storeDir, "tex0.png", 1024);
            writePng(storeDir, "tex1.png", 1024);
            int tex0 = stores.getTextureStore().register("tex0.png");
            int tex1 = stores.getTextureStore().register("tex1.png");

            // One feature whose mesh references two full-page textures: the
            // atlas overflows but a single feature cannot be subdivided.
            TriangleMesh mesh = quadrantTriangle(1L, tex0, 0.25, 0.25);
            int v0 = mesh.addVertex(0.7, 0.7, 0.0, 0, 0, 1, 0.0f, 0.0f);
            int v1 = mesh.addVertex(0.8, 0.7, 0.0, 0, 0, 1, 1.0f, 0.0f);
            int v2 = mesh.addVertex(0.7, 0.8, 0.0, 0, 0, 1, 0.0f, 1.0f);
            mesh.addTriangle(v0, v1, v2, 1L, tex1, false, SURFACE);
            long handle = stores.getMeshStore().store(mesh, 0);

            stores.initNodeEntryStore(2);
            stores.getNodeEntryStore().writeNode(1, List.of(new NodeEntry(1L, handle, 0L)));
            stores.setFeatureTextured(1L);

            PipelineContext ctx = context(stores, AtlasOverflowMode.SPLIT, 1);
            new AtlasOverflowSplitStage().execute(ctx);

            assertEquals(Set.of(1), ctx.meshNodeIndices());
            assertEquals(2, ctx.allNodes().size());
            assertTrue(ctx.allNodes().get(1).getChildren().isEmpty());
        }
    }

    // ---- fixture -----------------------------------------------------------

    /** Cell with four per-quadrant features whose 1024² textures overflow. */
    private static PipelineContext overflowingCell(VisExportStores stores, AtlasOverflowMode mode)
            throws IOException {
        return cell(stores, mode, 1024);
    }

    /**
     * Two overflowing cell roots (indices 1 and 2), the second shifted +2° in
     * X, with four per-quadrant features each: ids 1-4 in cell 1, 5-8 in
     * cell 2. Exercises the parallel per-cell-tree workers plus the serial
     * commit pass with more than one plan.
     */
    private static PipelineContext twoOverflowingCells(VisExportStores stores, AtlasOverflowMode mode)
            throws IOException {
        Path storeDir = stores.getTempDir();
        stores.initNodeEntryStore(3);
        for (int cellIndex = 1; cellIndex <= 2; cellIndex++) {
            double offsetX = (cellIndex - 1) * 2.0;
            List<NodeEntry> entries = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                long featureId = (cellIndex - 1) * 4L + i + 1;
                String name = "tex" + featureId + ".png";
                writePng(storeDir, name, 1024);
                int texId = stores.getTextureStore().register(name);
                TriangleMesh mesh = quadrantTriangle(featureId, texId,
                        offsetX + CENTROIDS[i][0], CENTROIDS[i][1]);
                long handle = stores.getMeshStore().store(mesh, i);
                entries.add(new NodeEntry(featureId, handle, 0L));
                stores.setFeatureTextured(featureId);
            }
            stores.getNodeEntryStore().writeNode(cellIndex, entries);
        }

        SceneNode globalRoot = new SceneNode(0, 0);
        SceneNode cellRoot1 = new SceneNode(1, 1);
        cellRoot1.setBoundingVolume(CELL_BBOX);
        globalRoot.addChild(cellRoot1);
        SceneNode cellRoot2 = new SceneNode(2, 1);
        cellRoot2.setBoundingVolume(BoundingVolume.ofBoundingBox(2.0, 0.0, 0.0, 3.0, 1.0, 10.0));
        globalRoot.addChild(cellRoot2);

        Tiles3DFormatOptions opts = new Tiles3DFormatOptions();
        opts.setAtlasOverflowMode(mode);
        opts.setMaxAtlasSize(1024);

        return new PipelineContext(stores, opts, new AttributeEncoder(), 8, 2)
                .setAllNodes(new ArrayList<>(List.of(globalRoot, cellRoot1, cellRoot2)))
                .setMeshNodeIndices(new HashSet<>(Set.of(1, 2)))
                .setHasTextures(true);
    }

    /**
     * The cell root became a content-less intermediate whose four leaves carry
     * exactly the given indices, each holding one entry, and together reference
     * exactly the cell's own features.
     */
    private static void assertCellTreeSplit(VisExportStores stores, SceneNode cellRoot,
                                            Set<Integer> expectedChildIndices,
                                            Set<Long> expectedFeatureIds) throws IOException {
        assertEquals(4, cellRoot.getChildren().size());
        assertFalse(cellRoot.isLodPreview());
        Set<Integer> childIndices = new HashSet<>();
        Set<Long> featureIds = new HashSet<>();
        for (SceneNode child : cellRoot.getChildren()) {
            childIndices.add(child.getIndex());
            List<NodeEntry> entries = stores.getNodeEntryStore().loadNode(child.getIndex());
            assertEquals(1, entries.size());
            featureIds.add(entries.get(0).id());
        }
        assertEquals(expectedChildIndices, childIndices);
        assertEquals(expectedFeatureIds, featureIds);
    }

    /**
     * Cell root (index 1) with four single-triangle features, one per quadrant,
     * each referencing its own {@code textureSize}² PNG registered relative to
     * the stores' temp directory (mirroring how the DB texture exporter
     * registers URIs).
     */
    private static PipelineContext cell(VisExportStores stores, AtlasOverflowMode mode,
                                        int textureSize) throws IOException {
        Path storeDir = stores.getTempDir();
        List<NodeEntry> entries = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            String name = "tex" + i + ".png";
            writePng(storeDir, name, textureSize);
            int texId = stores.getTextureStore().register(name);
            TriangleMesh mesh = quadrantTriangle(i + 1, texId, CENTROIDS[i][0], CENTROIDS[i][1]);
            long handle = stores.getMeshStore().store(mesh, i);
            entries.add(new NodeEntry(i + 1, handle, 0L));
            stores.setFeatureTextured(i + 1);
        }

        stores.initNodeEntryStore(2);
        stores.getNodeEntryStore().writeNode(1, entries);
        return context(stores, mode, 4);
    }

    private static PipelineContext context(VisExportStores stores, AtlasOverflowMode mode,
                                           long totalFeatures) {
        SceneNode globalRoot = new SceneNode(0, 0);
        SceneNode cellRoot = new SceneNode(1, 1);
        cellRoot.setBoundingVolume(CELL_BBOX);
        globalRoot.addChild(cellRoot);

        Tiles3DFormatOptions opts = new Tiles3DFormatOptions();
        opts.setAtlasOverflowMode(mode);
        // setMaxAtlasSize floors at 1024 — the smallest page the stage can see.
        opts.setMaxAtlasSize(1024);

        return new PipelineContext(stores, opts, new AttributeEncoder(), totalFeatures, 2)
                .setAllNodes(new ArrayList<>(List.of(globalRoot, cellRoot)))
                .setMeshNodeIndices(new HashSet<>(Set.of(1)))
                .setHasTextures(true);
    }

    /** Single textured triangle around the given centroid, UVs spanning [0,1]. */
    private static TriangleMesh quadrantTriangle(long featureId, int texId, double cx, double cy) {
        TriangleMesh mesh = new TriangleMesh();
        int v0 = mesh.addVertex(cx - 0.05, cy - 0.05, 0.0, 0, 0, 1, 0.0f, 0.0f);
        int v1 = mesh.addVertex(cx + 0.05, cy - 0.05, 0.0, 0, 0, 1, 1.0f, 0.0f);
        int v2 = mesh.addVertex(cx - 0.05, cy + 0.05, 0.0, 0, 0, 1, 0.0f, 1.0f);
        mesh.addTriangle(v0, v1, v2, featureId, texId, false, SURFACE);
        return mesh;
    }

    private static void writePng(Path dir, String name, int size) throws IOException {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image, "png", dir.resolve(name).toFile());
    }
}
