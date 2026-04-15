/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.writer.i3s;

import org.citydb.vis.writer.VisWriter;

import org.citydb.core.file.OutputFile;
import org.citydb.io.writer.WriteException;
import org.citydb.io.writer.WriteOptions;
import org.citydb.vis.encoder.i3s.I3SAttributeEncoder;
import org.citydb.vis.encoder.i3s.I3SGeometryEncoder;
import org.citydb.vis.encoder.i3s.I3SJsonSerializer;
import org.citydb.vis.model.AttrField;
import org.citydb.vis.model.FeatureData;
import org.citydb.vis.model.i3s.SceneLayer;
import org.citydb.vis.scene.SceneNode;
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

    /**
     * LOD threshold used by I3S runtimes (CesiumJS, ArcGIS) as a screen-space
     * area (px²) above which a node should refine to its children. Must be
     * integer — ArcGIS rejects float values in node pages.
     */
    private static final int LEAF_NODE_LOD_THRESHOLD = 131_072;
    private static final int INTERNAL_NODE_LOD_THRESHOLD = 65_536;

    private final Logger logger = LoggerFactory.getLogger(I3SWriter.class);
    private final I3SAttributeEncoder i3sAttributeEncoder;
    private final I3SGeometryEncoder geometryEncoder;
    private final I3SJsonSerializer jsonSerializer;

    public I3SWriter(OutputFile outputFile, WriteOptions options) throws WriteException {
        this(validateOutputFile(outputFile, "I3S"),
                loadFormatOptions(options, I3SFormatOptions.class, I3SFormatOptions::new, "I3S"),
                new I3SAttributeEncoder());
    }

    private I3SWriter(OutputFile outputFile,
                      I3SFormatOptions formatOptions,
                      I3SAttributeEncoder attributeEncoder) throws WriteException {
        super(outputFile, formatOptions, attributeEncoder);
        this.i3sAttributeEncoder = attributeEncoder;
        this.geometryEncoder = new I3SGeometryEncoder();
        this.jsonSerializer = new I3SJsonSerializer();
    }

    // ---- Format-specific output (Phase 5) -----------------------------------

    @Override
    protected void writeOutput(List<SceneNode> allNodes,
                               Set<Integer> meshNodeIndices,
                               double[] extent,
                               List<AttrField> attrFields,
                               boolean hasTextures) throws IOException {
        // Set I3S LOD thresholds on the tree
        setLodThresholds(allNodes);

        SceneLayer sceneLayer = buildSceneLayer(extent);
        writeI3SFolder(sceneLayer, allNodes, attrFields, meshNodeIndices, hasTextures);

        if (((I3SFormatOptions) getFormatOptions()).isSlpk()) {
            packageAsSlpk();
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

    private static void setLodThresholds(List<SceneNode> nodes) {
        for (SceneNode node : nodes) {
            boolean isLeaf = node.getChildren().isEmpty();
            node.setLodThreshold(isLeaf ? LEAF_NODE_LOD_THRESHOLD : INTERNAL_NODE_LOD_THRESHOLD);
        }
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
                                boolean hasTextures) throws IOException {
        Path outputDir = FileHelper.stripExtension(getOutputFile().getFile());
        Path layerDir = outputDir.resolve("layers").resolve("0");
        Files.createDirectories(layerDir);

        // Scene layer JSON
        jsonSerializer.writeSceneLayerJson(layerDir, sceneLayer, attrFields,
                hasTextures);

        // Parallel: encode geometry + write features/attributes per node.
        Set<Integer> effectiveMeshIndices = processNodesParallel(allNodes, meshNodeIndices,
                node -> writeNodeOutput(node, layerDir, attrFields));

        // Node pages AFTER geometry so vertex counts are accurate
        jsonSerializer.writeNodePages(layerDir, allNodes, effectiveMeshIndices, hasTextures);
    }

    /**
     * Process a single mesh node end-to-end: merge meshes from the sharded
     * store, build the texture atlas (if any), encode Draco geometry, and
     * write per-node feature/attribute JSON files.
     */
    private boolean writeNodeOutput(SceneNode node, Path layerDir,
                                    List<AttrField> attrFields) throws IOException {
        PreparedNode prepared = prepareNodeMesh(node);

        node.setMesh(prepared.mesh());
        List<Long> validFeatureIds = geometryEncoder.writeNodeGeometry(layerDir, node);
        if (validFeatureIds == null) {
            return false;
        }

        // Geometry confirmed — now safe to materialize the atlas file.
        if (prepared.atlas() != null) {
            Path textureDir = layerDir.resolve("nodes")
                    .resolve(String.valueOf(node.getIndex())).resolve("textures");
            Files.createDirectories(textureDir);
            prepared.atlas().write(textureDir.resolve("0"));
            // Record the texel count (width × height) so the node page can
            // emit texelCountHint, which ArcGIS Pro requires for rendering.
            node.setTexelCountHint(
                    prepared.atlas().getWidth() * prepared.atlas().getHeight());
        }

        List<FeatureData> featureDataList = loadNodeFeatures(prepared.entries());

        // Align features with valid face ranges — degenerate filtering may
        // have removed all triangles for some features, causing fewer face
        // ranges than input features. Feature/attribute output must match
        // the Draco feature-index attribute order.
        if (validFeatureIds.size() < featureDataList.size()) {
            featureDataList = FeatureData.reorderByIds(featureDataList, validFeatureIds);
            node.setFeatureCount(featureDataList.size());
        }

        jsonSerializer.writeNodeFeatures(layerDir, node, featureDataList);
        i3sAttributeEncoder.writeNodeAttributes(layerDir, node, attrFields,
                featureDataList);
        return true;
    }

}
