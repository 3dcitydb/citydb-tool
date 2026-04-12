/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.encoder;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import org.citydb.vis.model.FeatureEntry;
import org.citydb.vis.model.I3SConstants;
import org.citydb.vis.model.NodePage;
import org.citydb.vis.model.SceneLayerDescriptor;
import org.citydb.vis.scene.I3SNode;
import org.citydb.vis.scene.SceneLayer;

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
                                    List<I3SAttributeEncoder.AttrField> attrFields,
                                    boolean hasTextures) throws IOException {
        SceneLayerDescriptor descriptor = SceneLayerDescriptor.of(sceneLayer, attrFields, hasTextures);
        writePojo(layerDir.resolve("index.json"), descriptor);
    }

    /**
     * Write node pages. Uses a {@code Set<Integer>} to determine which nodes
     * have geometry (instead of the full nodeFeatureMap).
     */
    public void writeNodePages(Path layerDir, List<I3SNode> nodes,
                               Set<Integer> meshNodeIndices,
                               boolean hasTextures) throws IOException {
        int nodesPerPage = I3SConstants.NODES_PER_PAGE;
        int pageCount = (nodes.size() + nodesPerPage - 1) / nodesPerPage;

        for (int page = 0; page < pageCount; page++) {
            int start = page * nodesPerPage;
            int end = Math.min(start + nodesPerPage, nodes.size());
            List<I3SNode> pageNodes = nodes.subList(start, end);

            NodePage nodePage = NodePage.of(pageNodes, meshNodeIndices, hasTextures);

            Path nodePageDir = layerDir.resolve("nodepages").resolve(String.valueOf(page));
            Files.createDirectories(nodePageDir);
            writePojo(nodePageDir.resolve("index.json"), nodePage);
        }
    }

    /**
     * Write per-node feature metadata to features/0/index.json.
     */
    public void writeNodeFeatures(Path layerDir, I3SNode node,
                                  List<FeatureData> features) throws IOException {
        List<FeatureEntry> entries = new ArrayList<>(features != null ? features.size() : 0);
        if (features != null) {
            for (FeatureData fd : features) {
                entries.add(FeatureEntry.from(fd));
            }
        }

        Path featuresDir = layerDir.resolve("nodes").resolve(String.valueOf(node.getIndex()))
                .resolve("features").resolve("0");
        Files.createDirectories(featuresDir);
        writePojo(featuresDir.resolve("index.json"), entries);
    }

    private static void writePojo(Path file, Object pojo) throws IOException {
        Files.writeString(file, JSON.toJSONString(pojo,
                JSONWriter.Feature.FieldBased,
                JSONWriter.Feature.PrettyFormatWith2Space));
    }
}
