/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.encoder.i3s;

import org.citydb.vis.encoder.AttrStats;
import org.citydb.vis.util.JsonHelper;
import org.citydb.vis.model.AttrField;
import org.citydb.vis.model.FeatureData;
import org.citydb.vis.model.i3s.FeatureEntry;
import org.citydb.vis.model.i3s.I3SConstants;
import org.citydb.vis.model.i3s.NodeFeatureDocument;
import org.citydb.vis.model.i3s.NodePage;
import org.citydb.vis.model.i3s.SceneLayer;
import org.citydb.vis.model.i3s.SceneLayerDescriptor;
import org.citydb.vis.model.i3s.StatisticsResource;
import org.citydb.vis.scene.SceneNode;
import org.citydb.vis.styling.DefaultObjectStyle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes all JSON files for an I3S scene layer: the scene layer descriptor,
 * node pages, and per-node feature data.
 */
public class I3SJsonSerializer {

    public void writeSceneLayerJson(Path layerDir, SceneLayer sceneLayer,
                                    List<AttrField> attrFields,
                                    boolean hasTextures,
                                    boolean hasColors,
                                    boolean hasStyleOverrides,
                                    boolean enableShading,
                                    DefaultObjectStyle defaultStyle) throws IOException {
        SceneLayerDescriptor descriptor = SceneLayerDescriptor.of(sceneLayer, attrFields,
                hasTextures, hasColors, hasStyleOverrides, enableShading, defaultStyle);
        JsonHelper.writePojo(layerDir.resolve("index.json"), descriptor);
    }

    /**
     * Write node pages. Uses a {@code Set<Integer>} to determine which nodes
     * have geometry (instead of the full nodeFeatureMap).
     *
     * @param includeObb emit {@code obb} (and suppress {@code mbs}); required
     *                   for ArcGIS consumers (Pro / Maps SDK JS / Online
     *                   Scene Viewer). When {@code false}, only {@code mbs}
     *                   is emitted (CesiumJS default — mis-culls when OBB is
     *                   present). Gated upstream via {@code --slpk || --obb}.
     */
    public void writeNodePages(Path layerDir, List<SceneNode> nodes,
                               Set<Integer> meshNodeIndices,
                               boolean hasTextures,
                               boolean includeObb) throws IOException {
        int nodesPerPage = I3SConstants.NODES_PER_PAGE;
        int pageCount = (nodes.size() + nodesPerPage - 1) / nodesPerPage;

        for (int page = 0; page < pageCount; page++) {
            int start = page * nodesPerPage;
            int end = Math.min(start + nodesPerPage, nodes.size());
            List<SceneNode> pageNodes = nodes.subList(start, end);

            NodePage nodePage = NodePage.of(pageNodes, meshNodeIndices, hasTextures, includeObb);

            Path nodePageDir = layerDir.resolve("nodepages").resolve(String.valueOf(page));
            Files.createDirectories(nodePageDir);
            JsonHelper.writePojo(nodePageDir.resolve("index.json"), nodePage);
        }
    }

    /**
     * Write per-node feature metadata to {@code features/0/index.json} in the
     * I3S 1.7 {@code NodeFeatureData} schema. Per-feature {@code mbb} is the
     * exact AABB of the feature's triangles (from
     * {@link org.citydb.vis.encoder.i3s.I3SGeometryEncoder.NodeGeometryResult})
     * and {@code position} is its centroid. The ArcGIS Maps SDK for JavaScript
     * uses these to build the per-node pick BVH; collapsing them to a shared
     * node bbox makes picks intermittently miss under oblique camera angles.
     */
    public void writeNodeFeatures(Path layerDir, SceneNode node,
                                  List<FeatureData> features,
                                  List<double[]> featureAabbs) throws IOException {
        List<FeatureEntry> entries = new ArrayList<>(features.size());
        for (int i = 0; i < features.size(); i++) {
            double[] aabb = featureAabbs.get(i);
            double[] position = {
                    (aabb[0] + aabb[3]) / 2.0,
                    (aabb[1] + aabb[4]) / 2.0,
                    (aabb[2] + aabb[5]) / 2.0};
            entries.add(new FeatureEntry(features.get(i).id(), position, aabb));
        }

        Path featuresDir = layerDir.resolve("nodes").resolve(String.valueOf(node.getIndex()))
                .resolve("features").resolve("0");
        Files.createDirectories(featuresDir);
        JsonHelper.writePojo(featuresDir.resolve("index.json"),
                NodeFeatureDocument.of(entries));
    }

    /**
     * Write a placeholder {@code shared/sharedResource.json} per node. I3S
     * 1.7+ deprecated sharedResource (materials live in layer-level
     * {@code materialDefinitions} / {@code geometryDefinitions} now), but the
     * spec still mandates the file be physically present for backwards
     * compatibility — Esri's SLPK validator reports {@code NOT_FOUND} for
     * every node otherwise and finally fails with "Too many resources are
     * missing". Cesium / ArcGIS JS don't read it.
     */
    public void writeNodeSharedResource(Path layerDir, SceneNode node) throws IOException {
        // Layout matches the project's other JSON resources: an "index.json"
        // inside a folder named after the resource. The SLPK packager
        // collapses {res}/index.json → {res}.json.gz; the SLPK spec wants
        // nodes/{i}/shared/sharedResource.json.gz, so the on-disk path is
        // nodes/{i}/shared/sharedResource/index.json.
        Path sharedResourceDir = layerDir.resolve("nodes").resolve(String.valueOf(node.getIndex()))
                .resolve("shared").resolve("sharedResource");
        Files.createDirectories(sharedResourceDir);
        // A non-empty materialDefinitions map is required: Esri's validator
        // rejects "{}" with "JSON Object is expected for anonymous field".
        // The placeholder is never read at runtime — actual materials live in
        // layer-level materialDefinitions (1.7+) — but the SLPK has to carry
        // it for spec compliance.
        Files.writeString(sharedResourceDir.resolve("index.json"),
                "{\"materialDefinitions\":{\"Mat0\":{\"type\":\"standard\",\"params\":{}}}}");
    }

    /**
     * Write per-attribute statistics resources to
     * {@code layers/0/statistics/f_K/0/index.json}, one per declared
     * attribute field. Required to satisfy the I3S 1.7 SLPK validator's
     * advisory {@code MISSING_ATTRIBUTE_STATS_DECL} check on every
     * field that has a corresponding {@code statisticsInfo} entry. If
     * the layer-level stats accumulator missed an attribute (e.g. all
     * features had a null value), an empty stats record is emitted —
     * still satisfies the validator's resource-presence check.
     */
    public void writeStatistics(Path layerDir, List<AttrField> attrFields,
                                Map<String, AttrStats.Result> stats) throws IOException {
        for (int i = 0; i < attrFields.size(); i++) {
            AttrField field = attrFields.get(i);
            AttrStats.Result result = stats.get(field.name());
            if (result == null) {
                // Field declared but never observed (e.g. every feature
                // had null for it). Emit an empty stats record so the
                // resource still exists and the validator stays quiet.
                result = field.type() == org.citydb.vis.model.AttrType.STRING
                        ? AttrStats.forString().toResult()
                        : AttrStats.forNumeric().toResult();
            }
            Path statsDir = layerDir.resolve("statistics")
                    .resolve("f_" + i).resolve("0");
            Files.createDirectories(statsDir);
            JsonHelper.writePojo(statsDir.resolve("index.json"),
                    StatisticsResource.of(result));
        }
    }

}
