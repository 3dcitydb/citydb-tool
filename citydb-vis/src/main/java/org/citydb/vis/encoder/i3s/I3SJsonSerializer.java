/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.encoder.i3s;

import org.citydb.vis.util.JsonHelper;
import org.citydb.vis.model.AttrField;
import org.citydb.vis.model.FeatureData;
import org.citydb.vis.model.i3s.FeatureEntry;
import org.citydb.vis.model.i3s.I3SConstants;
import org.citydb.vis.model.i3s.NodeFeatureDocument;
import org.citydb.vis.model.i3s.NodePage;
import org.citydb.vis.model.i3s.SceneLayer;
import org.citydb.vis.model.i3s.SceneLayerDescriptor;
import org.citydb.vis.scene.SceneNode;
import org.citydb.vis.styling.DefaultObjectStyle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
                                    DefaultObjectStyle defaultStyle) throws IOException {
        SceneLayerDescriptor descriptor = SceneLayerDescriptor.of(sceneLayer, attrFields,
                hasTextures, hasColors, hasStyleOverrides, defaultStyle);
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

}
