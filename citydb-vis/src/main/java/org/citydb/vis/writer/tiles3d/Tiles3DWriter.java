/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.writer.tiles3d;

import org.citydb.vis.writer.VisWriter;

import org.citydb.core.file.OutputFile;
import org.citydb.io.writer.WriteException;
import org.citydb.io.writer.WriteOptions;
import org.citydb.vis.config.Tiles3DFormatOptions;
import org.citydb.vis.pipeline.PipelineContext;
import org.citydb.vis.VisExportException;
import org.citydb.vis.appearance.AtlasFallbackStrategy;
import org.citydb.vis.appearance.AtlasMode;
import org.citydb.vis.appearance.TextureAtlas;
import org.citydb.vis.appearance.TextureAtlasBuilder;
import org.citydb.vis.attribute.AttributeEncoder;
import org.citydb.vis.encoder.tiles3d.GlbEncoder;
import org.citydb.vis.encoder.tiles3d.TilePaths;
import org.citydb.vis.encoder.tiles3d.TilesetSerializer;
import org.citydb.vis.model.AttrField;
import org.citydb.vis.scene.SceneNode;
import org.citydb.vis.util.FileHelper;
import org.citydb.vis.util.GeoTransform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Writes city model features to the OGC 3D Tiles 1.1 format.
 * <p>
 * Extends the format-agnostic {@link VisWriter} pipeline with 3D Tiles output:
 * per-node GLB files (hand-written glTF 2.0 Binary with {@code EXT_mesh_features}
 * and {@code EXT_structural_metadata}), and a multi-level tileset hierarchy
 * ({@code tileset.json} + {@code subtrees/*.json}).
 * <p>
 * The tileset hierarchy lazy-loads the scene via two nested layers: a
 * spatial aggregation tree grouping populated grid cells (built once by
 * the shared {@link org.citydb.vis.pipeline.stages.AggregationStage}),
 * and a per-level subtree split emitted by {@link TilesetSerializer}
 * that gives every aggregation node its own {@code subtree.json} and
 * keeps each file uniformly shaped (root + direct children). The root
 * {@code tileset.json} stays at a single external ref regardless of
 * cell count.
 * <p>
 * <b>Coordinate system:</b> all GLB vertex positions are in a local ENU
 * (East-North-Up) frame relative to their grid cell's center. Each cell root
 * carries its own ENU-to-ECEF {@code transform}, tangent to the WGS84
 * ellipsoid at that cell center; descendants inherit it via 3D Tiles'
 * hierarchical transform composition (aggregation nodes and the root tile
 * stay identity). Anchoring per cell keeps vertex magnitudes bounded by the
 * cell size (float32-safe at any dataset extent) and re-establishes the
 * tangent plane at every cell, so Earth curvature no longer lifts cells far
 * from the dataset center off the ground — the previous single-root-transform
 * design floated them by the sagitta d²/2R (tens to hundreds of metres once
 * cells sat tens of kilometres from the dataset center).
 */
public class Tiles3DWriter extends VisWriter {
    private final Logger logger = LoggerFactory.getLogger(Tiles3DWriter.class);
    private final Tiles3DFormatOptions formatOptions;
    private final GlbEncoder glbEncoder;
    private final TilesetSerializer tilesetSerializer;
    // JPEG bytes per prototype atlas, shared across the parallel per-node
    // writes (identity-keyed: atlas instances are canonical per prototype).
    private final Map<TextureAtlas, byte[]> protoAtlasJpegCache = new ConcurrentHashMap<>();

    public Tiles3DWriter(OutputFile outputFile, WriteOptions options) throws WriteException {
        this(validateOutputFile(outputFile, "3D Tiles"),
                loadFormatOptions(options, Tiles3DFormatOptions.class,
                        Tiles3DFormatOptions::new, "3D Tiles"),
                new AttributeEncoder(),
                options);
    }

    private Tiles3DWriter(OutputFile outputFile,
                          Tiles3DFormatOptions formatOptions,
                          AttributeEncoder attributeEncoder,
                          WriteOptions writeOptions) throws WriteException {
        super(outputFile, formatOptions, attributeEncoder, writeOptions);
        this.formatOptions = formatOptions;
        this.glbEncoder = new GlbEncoder();
        this.tilesetSerializer = new TilesetSerializer();
    }

    /**
     * GPU instancing for implicit geometries is off by default;
     * {@code --implicit-geometry-instancing} opts in, replacing per-instance
     * baked meshes with shared prototype meshes.
     */
    @Override
    protected boolean supportsImplicitGeometryInstancing() {
        return formatOptions.isImplicitGeometryInstancing();
    }

    /**
     * 3D Tiles never needs the atlas's white-pixel sentinel: untextured
     * triangles are partitioned into a separate GLB primitive (with its own
     * untextured PBR material) by {@code GlbEncoder} and never sample the
     * atlas. Reserving the sentinel just wastes BSP space
     * and can push a borderline-fitting atlas over the edge into needless
     * expansion.
     */
    @Override
    protected boolean atlasNeedsWhitePixelSentinel() {
        return false;
    }

    // ---- Format-specific output (Phase 5) -----------------------------------

    @Override
    protected void writeOutput(PipelineContext ctx) throws VisExportException {
        List<SceneNode> allNodes = ctx.allNodes();
        Set<Integer> meshNodeIndices = ctx.meshNodeIndices();
        double[] extent = ctx.extent();
        List<AttrField> attrFields = ctx.attrFields();
        Path outputDir = FileHelper.stripExtension(getOutputFile().getFile());
        Path tilesDir = outputDir.resolve("tiles");
        Path subtreesDir = outputDir.resolve("subtrees");
        try {
            Files.createDirectories(tilesDir);
            Files.createDirectories(subtreesDir);
        } catch (IOException e) {
            throw new VisExportException("Failed to create 3D Tiles output directories.", e);
        }

        // The shared AggregationStage has already wrapped the cell roots
        // under a single aggregation root attached as globalRoot's only
        // child. TilePaths walks that aggregation subtree to assign each
        // tile its position under tiles/ and subtrees/.
        SceneNode globalRoot = allNodes.get(0);
        SceneNode aggRoot = globalRoot.getChildren().get(0);
        Map<Integer, int[]> tilePaths = TilePaths.buildPathIndex(aggRoot);

        // Per-cell placement: each populated grid cell (cell root) gets its own
        // ENU-to-ECEF transform tangent to the WGS84 ellipsoid at the cell
        // center. A single dataset-wide transform (the old design) is only
        // tangent at one point, so cells far from it float above the ground by
        // the Earth-curvature sagitta d²/2R (hundreds of metres at state scale).
        // cellRootGridCoords' keys are exactly the cell-root node indices.
        Set<Integer> cellRootIndices = ctx.cellRootGridCoords().keySet();
        Map<Integer, double[]> cellTransforms = new HashMap<>();
        Map<Integer, double[]> cellAnchors = new HashMap<>();
        for (int cellRootIndex : cellRootIndices) {
            var bv = allNodes.get(cellRootIndex).getBoundingVolume();
            double[] anchor = {bv.getCenterX(), bv.getCenterY(), bv.getCenterZ()};
            cellAnchors.put(cellRootIndex, anchor);
            cellTransforms.put(cellRootIndex, GeoTransform.enuToEcefMatrix(anchor));
        }

        // Map every mesh node to its cell root's anchor by walking up the tree.
        // All content in a cell (the cell root plus any mixed-texture / atlas-
        // overflow / LOD-preview descendants) is encoded relative to the same
        // cell center so it inherits that cell root's single tile transform.
        // Every mesh node descends from a cell root by construction, so the
        // walk always resolves (a null cur would be an invariant violation).
        Map<Integer, double[]> nodeAnchor = new HashMap<>();
        for (int meshIndex : meshNodeIndices) {
            SceneNode cur = allNodes.get(meshIndex);
            while (cur != null && !cellRootIndices.contains(cur.getIndex())) {
                cur = cur.getParent();
            }
            nodeAnchor.put(meshIndex, cellAnchors.get(cur.getIndex()));
        }

        // Pre-create parent directories serially so the parallel GLB
        // writer below doesn't contend on filesystem stat syscalls.
        try {
            createTileParentDirs(tilesDir, tilePaths, meshNodeIndices);
        } catch (IOException e) {
            throw new VisExportException("Failed to create tile output directories.", e);
        }

        // Parallel: encode GLB per node
        Set<Integer> effectiveMeshIndices = nodeAssembler().processNodesParallel(allNodes, meshNodeIndices,
                node -> writeNodeGlb(node, tilesDir, tilePaths, attrFields,
                        nodeAnchor.get(node.getIndex())));

        try {
            // Write sub-tilesets (tree-based spatial split) starting at the
            // aggregation root; the writer recurses through the aggregation
            // layer and cell leaves uniformly.
            // geRatio = 16/threshold ties the 3D Tiles refine decision to the
            // same projected MBS radius as I3S: both formats refine when the
            // on-screen MBS radius exceeds screenPixelThreshold pixels (assuming
            // Cesium's default runtime maximumScreenSpaceError = 16).
            // screenPixelThreshold == 0 is the "always refine" sentinel: pass
            // POSITIVE_INFINITY so TileNode.computeGeometricError emits a
            // finite "always-refine" geometric error (Infinity is not valid
            // JSON) and the runtime always loads the leaves.
            double pixelThreshold = getFormatOptions().getScreenPixelThreshold();
            double geRatio = pixelThreshold > 0 ? 16.0 / pixelThreshold : Double.POSITIVE_INFINITY;
            AtomicInteger subtreeFileCount = new AtomicInteger();
            tilesetSerializer.writeSubTileset(subtreesDir, aggRoot,
                    effectiveMeshIndices, tilePaths, cellTransforms,
                    subtreeFileCount, geRatio);

            // Write root tileset.json: a single external ref to the aggregation
            // root's subtree file keeps the root bounded independent of cell count.
            // The root tile is identity; ECEF placement is per cell (see above).
            tilesetSerializer.writeRootTileset(outputDir, globalRoot, aggRoot,
                    extent, attrFields, tilePaths, geRatio);

            logger.info("3D Tiles output: {} tiles, {} sub-tileset files, tileset.json written.",
                    effectiveMeshIndices.size(), subtreeFileCount.get());
        } catch (IOException e) {
            throw new VisExportException("Failed to write 3D Tiles tileset.", e);
        }
    }

    /**
     * Process a single mesh node: merge meshes, build texture atlas, encode GLB.
     * <p>
     * Atlas mode is picked from the user's {@code --atlas-fallback} setting:
     * <ul>
     *   <li>{@code expand} → {@link AtlasMode#AUTO}: the GLB encoder spills
     *       overflow onto additional atlas pages
     *       ({@link TextureAtlasBuilder#buildMulti}) on every overflowing cell,
     *       preserving source-resolution textures. Under
     *       {@code --atlas-overflow-mode=split}/{@code hybrid} this only
     *       affects residual cells the
     *       {@link org.citydb.vis.pipeline.stages.AtlasOverflowSplitStage}
     *       could not subdivide further; under {@code flat} it applies to
     *       every overflowing cell (no spatial subdivision).</li>
     *   <li>{@code rescale} → {@link AtlasMode#SINGLE_ATLAS}: every node
     *       gets exactly one atlas page; overflow is resolved by shrinking
     *       textures to fit {@code --max-atlas-size}, matching the I3S
     *       behavior.</li>
     * </ul>
     * I3S cannot offer the {@code AUTO} path because its spec mandates one
     * material per node, but the fallback strategy itself is symmetric:
     * I3S {@code expand} grows the single atlas page (up to the WebGL 16K
     * cap) instead of spilling onto additional pages.
     */
    private boolean writeNodeGlb(SceneNode node, Path tilesDir,
                                 Map<Integer, int[]> tilePaths,
                                 List<AttrField> attrFields, double[] cellAnchor)
            throws VisExportException {
        AtlasMode mode = getFormatOptions().getAtlasFallbackStrategy()
                == AtlasFallbackStrategy.EXPAND
                ? AtlasMode.AUTO
                : AtlasMode.SINGLE_ATLAS;
        return nodeAssembler().writeNode(node, mode, "3D Tiles", (prepared, features) -> {
            // Serialize each atlas page to JPEG bytes and index texture ids by
            // page so the GLB encoder can route textured triangles to the
            // primitive backed by the correct atlas.
            List<byte[]> atlasBytesList = new ArrayList<>(prepared.atlases().size());
            Map<Integer, Integer> texIdToPage = new HashMap<>();
            for (int p = 0; p < prepared.atlases().size(); p++) {
                TextureAtlas atlas = prepared.atlases().get(p);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                atlas.write(baos);
                atlasBytesList.add(baos.toByteArray());
                for (int texId : atlas.getTextureIds()) {
                    texIdToPage.put(texId, p);
                }
            }

            // Serialize each instanced prototype's own atlas to JPEG bytes,
            // index-aligned with the batches (null = untextured prototype).
            // Cached per atlas: the same prototype recurs across many cells
            // and the JPEG encode is identical every time. Deliberately NOT
            // computeIfAbsent — its lambda cannot throw the checked
            // IOException that must reach writeNode's VisExportException
            // wrapper (the WriteException boundary). A racing duplicate
            // encode is idempotent and putIfAbsent keeps one canonical copy.
            List<byte[]> instanceAtlasBytes = new ArrayList<>(prepared.instanceBatches().size());
            for (TextureAtlas protoAtlas : prepared.instanceAtlases()) {
                if (protoAtlas == null) {
                    instanceAtlasBytes.add(null);
                    continue;
                }
                byte[] bytes = protoAtlasJpegCache.get(protoAtlas);
                if (bytes == null) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    protoAtlas.write(baos);
                    bytes = baos.toByteArray();
                    byte[] existing = protoAtlasJpegCache.putIfAbsent(protoAtlas, bytes);
                    if (existing != null) {
                        bytes = existing;
                    }
                }
                instanceAtlasBytes.add(bytes);
            }

            // Encode GLB
            node.setMesh(prepared.mesh());
            byte[] glb = glbEncoder.encode(node, atlasBytesList, texIdToPage,
                    features, attrFields, prepared.instanceBatches(), instanceAtlasBytes,
                    cellAnchor,
                    getFormatOptions().getStyleRegistry(),
                    getFormatOptions().isEnableShading());
            if (glb == null) {
                return false;
            }

            Files.write(tilesDir.resolve(TilePaths.tileFile(tilePaths.get(node.getIndex()))), glb);
            return true;
        });
    }

    private static void createTileParentDirs(Path tilesDir,
                                             Map<Integer, int[]> tilePaths,
                                             Set<Integer> meshNodeIndices) throws IOException {
        Set<String> dirs = new HashSet<>();
        for (int idx : meshNodeIndices) {
            int[] path = tilePaths.get(idx);
            if (path.length >= 2) {
                dirs.add(TilePaths.parentDir(path));
            }
        }
        // Create shallow directories first so each deeper createDirectories
        // call finds its ancestors already present (fewer redundant stats).
        List<String> sorted = new ArrayList<>(dirs);
        sorted.sort(Comparator.comparingInt(String::length));
        for (String rel : sorted) {
            Files.createDirectories(tilesDir.resolve(rel));
        }
    }

}
