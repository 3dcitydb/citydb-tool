/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.writer.i3s;

import org.citydb.vis.writer.VisWriter;

import org.citydb.core.file.OutputFile;
import org.citydb.io.writer.WriteException;
import org.citydb.io.writer.WriteOptions;
import org.citydb.vis.config.I3SFormatOptions;
import org.citydb.vis.pipeline.PipelineContext;
import org.citydb.vis.VisExportException;
import org.citydb.vis.appearance.AtlasMode;
import org.citydb.vis.appearance.TextureAtlas;
import org.citydb.vis.encoder.i3s.I3SAttributeEncoder;
import org.citydb.vis.encoder.i3s.I3SGeometryEncoder;
import org.citydb.vis.encoder.i3s.I3SJsonSerializer;
import org.citydb.vis.model.AttrField;
import org.citydb.vis.model.FeatureData;
import org.citydb.vis.model.i3s.SceneLayer;
import org.citydb.vis.scene.SceneNode;
import org.citydb.vis.styling.ObjectStyleRegistry;
import org.citydb.vis.util.FileHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Writes city model features to the OGC I3S (Indexed 3D Scene Layer) format.
 * <p>
 * Extends the format-agnostic {@link VisWriter} pipeline with I3S-specific
 * output: Draco-compressed geometry, I3S JSON metadata (scene layer descriptor,
 * node pages, per-node features), binary attribute buffers, and texture files.
 * <p>
 * <b>Coordinate system:</b> the current implementation hard-codes the output
 * CRS to EPSG:4326 (WGS 84, lon/lat/ellipsoid-height). This is a simplification
 * of the writer, not a limitation of the I3S specification — I3S allows any
 * valid {@code spatialReference} (geographic or projected). The choice is
 * driven by two practical constraints:
 * <ul>
 *   <li>CesiumJS's I3S loader is most mature on the global / geographic path
 *       (ENU-to-ECEF at each node center); projected CRS support is less
 *       well-trodden.</li>
 *   <li>All internal math in this module — ENU-to-ECEF normal rotation in
 *       {@link I3SGeometryEncoder}, the {@code 111320·cos(lat)} degree-to-meter
 *       conversions in {@link org.citydb.vis.geometry.PolygonTriangulator} and
 *       {@link org.citydb.vis.scene.BoundingVolume}, and the Draco position
 *       quantization with {@code i3s-scale_x/y} metadata — assumes
 *       {@code X=longitude°, Y=latitude°, Z=meters}.</li>
 * </ul>
 * Supporting other CRS would require revisiting every site above, not just
 * changing the declared {@code wkid}.
 */
public class I3SWriter extends VisWriter {
    private static final int EPSG_4326 = 4326;

    // LOD threshold derives from VisFormatOptions.lodRefineRadius via
    // lodThresholdFor(r) to align the I3S refine boundary with the 3D Tiles
    // one — both refine at projected MBS radius > lodRefineRadius pixels.
    // See lodThresholdFor below. Must be an integer — ArcGIS rejects float
    // values in node pages.

    private final Logger logger = LoggerFactory.getLogger(I3SWriter.class);
    private final I3SAttributeEncoder i3sAttributeEncoder;
    private final I3SGeometryEncoder geometryEncoder;
    private final I3SJsonSerializer jsonSerializer;

    public I3SWriter(OutputFile outputFile, WriteOptions options) throws WriteException {
        this(validateOutputFile(outputFile, "I3S"),
                loadFormatOptions(options, I3SFormatOptions.class, I3SFormatOptions::new, "I3S"),
                new I3SAttributeEncoder(),
                options);
    }

    private I3SWriter(OutputFile outputFile,
                      I3SFormatOptions formatOptions,
                      I3SAttributeEncoder attributeEncoder,
                      WriteOptions writeOptions) throws WriteException {
        super(outputFile, formatOptions, attributeEncoder, writeOptions);
        this.i3sAttributeEncoder = attributeEncoder;
        this.geometryEncoder = new I3SGeometryEncoder();
        this.jsonSerializer = new I3SJsonSerializer();
    }

    // ---- Format-specific output (Phase 5) -----------------------------------

    @Override
    protected void writeOutput(PipelineContext ctx) throws VisExportException {
        List<SceneNode> allNodes = ctx.allNodes();
        Set<Integer> meshNodeIndices = ctx.meshNodeIndices();
        double[] extent = ctx.extent();
        List<AttrField> attrFields = ctx.attrFields();
        boolean hasTextures = ctx.hasTextures();
        I3SFormatOptions options = (I3SFormatOptions) getFormatOptions();
        ObjectStyleRegistry styleRegistry = options.getStyleRegistry();
        // "Layer bakes per-vertex colour" widens the X3DMaterial signal so
        // that intra-feature-mixed textured nodes can also carry COLOR_0:
        // their white-pixel-UV triangles bake --default-color / per-type
        // styles into COLOR_0 (texture sample is white, so white × COLOR_0
        // = COLOR_0). Without this widening, the textured slot stays at
        // GeometryDefinition.textured() with no COLOR_0 in compressedAttributes,
        // and Cesium ignores any per-vertex colour we encode.
        boolean hasColors = ctx.hasColors()
                || styleRegistry.defaultStyle().hasNonDefaultColor()
                || styleRegistry.hasOverrides();
        // Set I3S LOD thresholds on the tree
        int lodThreshold = lodThresholdFor(getFormatOptions().getLodRefineRadius());
        setLodThresholds(allNodes, lodThreshold);

        boolean slpk = options.isSlpk();
        // OBB policy is target-dependent and the two consumers are mutually
        // incompatible: ArcGIS (Pro, Maps SDK JS, Online Scene Viewer) needs
        // OBB to render anything, while CesiumJS mis-culls buildings at some
        // camera angles when OBB is present. SLPK always emits it (ArcGIS
        // Pro requirement); folder mode emits it only when --obb is passed.
        boolean includeObb = slpk || options.isObb();
        SceneLayer sceneLayer = buildSceneLayer(extent);
        writeI3SFolder(sceneLayer, allNodes, attrFields, meshNodeIndices,
                hasTextures, hasColors, includeObb, styleRegistry);

        if (slpk) {
            try {
                packageAsSlpk();
            } catch (IOException e) {
                throw new VisExportException("Failed to package I3S output as SLPK.", e);
            }
        }
    }

    /**
     * Package the just-written I3S folder as an SLPK file at the output path
     * and remove the intermediate folder.
     */
    private void packageAsSlpk() throws IOException {
        Path outputDir = FileHelper.stripExtension(getOutputFile().getFile());
        Path slpkFile = getOutputFile().getFile();
        // If no extension was given (-o data), the folder and SLPK paths
        // would collide — append .slpk explicitly in that case.
        if (slpkFile.equals(outputDir)) {
            slpkFile = outputDir.resolveSibling(outputDir.getFileName() + ".slpk");
        }

        logger.info("Packaging I3S output as SLPK: {}", slpkFile);
        SlpkPackager.pack(outputDir, slpkFile);

        logger.info("Removing intermediate folder: {}", outputDir);
        FileHelper.deleteDirectoryTree(outputDir);
    }

    private static void setLodThresholds(List<SceneNode> nodes, int lodThreshold) {
        for (SceneNode node : nodes) {
            node.setLodThreshold(lodThreshold);
        }
    }

    /**
     * Derive the I3S {@code lodThreshold} (pixels²) from the unified
     * {@code lodRefineRadius} parameter so I3S refines at the same
     * projected MBS radius as 3D Tiles.
     * <p>
     * Both formats refine when the projected MBS radius exceeds
     * {@code lodRefineRadius} pixels. The projected disk's area is
     * {@code π × r²}, which is what I3S compares against.
     */
    private static int lodThresholdFor(double refineRadiusPx) {
        return (int) Math.round(Math.PI * refineRadiusPx * refineRadiusPx);
    }

    private static SceneLayer buildSceneLayer(double[] extent) {
        SceneLayer layer = new SceneLayer();
        layer.setName("3DCityDB I3S Export");
        layer.setDescription("Exported from 3DCityDB using citydb-tool");
        layer.setExtent(extent);
        layer.setWkid(EPSG_4326);
        return layer;
    }

    /**
     * Write I3S folder structure with fully parallel node processing.
     */
    private void writeI3SFolder(SceneLayer sceneLayer, List<SceneNode> allNodes,
                                List<AttrField> attrFields,
                                Set<Integer> meshNodeIndices,
                                boolean hasTextures,
                                boolean hasColors,
                                boolean includeObb,
                                ObjectStyleRegistry styleRegistry) throws VisExportException {
        Path outputDir = FileHelper.stripExtension(getOutputFile().getFile());
        Path layerDir = outputDir.resolve("layers").resolve("0");

        boolean hasStyleOverrides = styleRegistry.hasOverrides();

        try {
            Files.createDirectories(layerDir);
            jsonSerializer.writeSceneLayerJson(layerDir, sceneLayer, attrFields,
                    hasTextures, hasColors, hasStyleOverrides, styleRegistry.defaultStyle());
        } catch (IOException e) {
            throw new VisExportException("Failed to write I3S scene layer JSON.", e);
        }

        // Parallel: encode geometry + write features/attributes per node.
        Set<Integer> effectiveMeshIndices = processNodesParallel(allNodes, meshNodeIndices,
                node -> writeNodeOutput(node, layerDir, attrFields, hasColors, styleRegistry));

        if (hasStyleOverrides && logger.isDebugEnabled()) {
            int textured = geometryEncoder.getTexturedNodesWithStyleConfig();
            int x3d = geometryEncoder.getX3DNodesWithStyleOverride();
            if (textured > 0) {
                logger.debug("--feature-type-style: {} textured node(s) cannot fully apply " +
                        "overrides (I3S is one-material-per-node; styling only bakes onto " +
                        "untextured / white-pixel triangles).", textured);
            }
            if (x3d > 0) {
                logger.debug("--feature-type-style: {} X3DMaterial node(s) bake overrides into " +
                        "COLOR_0 but render unlit (X3DMaterial precedence forces no NORMAL).", x3d);
            }
        }

        try {
            // Node pages AFTER geometry so vertex counts are accurate
            jsonSerializer.writeNodePages(layerDir, allNodes, effectiveMeshIndices,
                    hasTextures, includeObb);
        } catch (IOException e) {
            throw new VisExportException("Failed to write I3S node pages.", e);
        }
    }

    /**
     * Process a single mesh node end-to-end: merge meshes from the sharded
     * store, build the texture atlas (if any), encode Draco geometry, and
     * write per-node feature/attribute JSON files.
     */
    private boolean writeNodeOutput(SceneNode node, Path layerDir,
                                    List<AttrField> attrFields,
                                    boolean layerHasColors,
                                    ObjectStyleRegistry styleRegistry) throws VisExportException {
        PreparedNode prepared = prepareNodeMesh(node, AtlasMode.SINGLE_ATLAS);
        List<FeatureData> featureDataList = loadNodeFeatures(prepared.entries());
        logAtlasViolations(node, prepared, featureDataList);

        try {
            node.setMesh(prepared.mesh());
            I3SGeometryEncoder.NodeGeometryResult geomResult =
                    geometryEncoder.writeNodeGeometry(layerDir, node, layerHasColors, styleRegistry);
            if (geomResult == null) {
                return false;
            }
            List<Long> validFeatureIds = geomResult.rangeFeatureIds();
            List<double[]> featureAabbs = geomResult.featureAabbs();

            // Geometry confirmed — now safe to materialize the atlas file.
            // I3S is guaranteed single-page (prepareNodeMesh called with
            // AtlasMode.SINGLE_ATLAS), so atlases.get(0) is the only page.
            if (!prepared.atlases().isEmpty()) {
                TextureAtlas atlas = prepared.atlases().get(0);
                Path textureDir = layerDir.resolve("nodes")
                        .resolve(String.valueOf(node.getIndex())).resolve("textures");
                Files.createDirectories(textureDir);
                atlas.write(textureDir.resolve("0"));
                // Record the texel count (width × height) so the node page can
                // emit texelCountHint, which ArcGIS Pro requires for rendering.
                node.setTexelCountHint(atlas.getWidth() * atlas.getHeight());
            }

            // Align features with valid face ranges — degenerate filtering may
            // have removed all triangles for some features, causing fewer face
            // ranges than input features. Feature/attribute output must match
            // the Draco feature-index attribute order.
            if (validFeatureIds.size() < featureDataList.size()) {
                featureDataList = FeatureData.reorderByIds(featureDataList, validFeatureIds);
                node.setFeatureCount(featureDataList.size());
            }

            jsonSerializer.writeNodeFeatures(layerDir, node, featureDataList, featureAabbs);
            i3sAttributeEncoder.writeNodeAttributes(layerDir, node, attrFields,
                    featureDataList);
            return true;
        } catch (IOException e) {
            throw new VisExportException("Failed to write I3S node " + node.getIndex() + ".", e);
        }
    }

}
