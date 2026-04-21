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
import org.citydb.vis.model.i3s.NodePage;
import org.citydb.vis.model.i3s.SceneLayer;
import org.citydb.vis.model.i3s.SceneLayerDescriptor;
import org.citydb.vis.scene.SceneNode;

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
                                    boolean hasTextures) throws IOException {
        SceneLayerDescriptor descriptor = SceneLayerDescriptor.of(sceneLayer, attrFields, hasTextures);
        JsonHelper.writePojo(layerDir.resolve("index.json"), descriptor);
    }

    /**
     * Write node pages. Uses a {@code Set<Integer>} to determine which nodes
     * have geometry (instead of the full nodeFeatureMap).
     *
     * @param includeObb emit {@code obb} alongside {@code mbs}; required for
     *                   ArcGIS Pro, suppressed for CesiumJS (OBB mis-culls).
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
     * Write per-node feature metadata to features/0/index.json.
     */
    public void writeNodeFeatures(Path layerDir, SceneNode node,
                                  List<FeatureData> features) throws IOException {
        List<FeatureEntry> entries = new ArrayList<>(features.size());
        for (FeatureData fd : features) {
            entries.add(FeatureEntry.from(fd));
        }

        Path featuresDir = layerDir.resolve("nodes").resolve(String.valueOf(node.getIndex()))
                .resolve("features").resolve("0");
        Files.createDirectories(featuresDir);
        JsonHelper.writePojo(featuresDir.resolve("index.json"), entries);
    }

}
