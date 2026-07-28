/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.writer;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.citydb.vis.I3SAdapter;
import org.citydb.vis.config.I3SFormatOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Database-free end-to-end smoke test for the I3S writer (see
 * {@link AbstractSyntheticSceneExportTest}). Asserts the OGC I3S scene-layer
 * structure the writer is contracted to produce — descriptor, node pages,
 * per-node geometry/feature/attribute resources and written textures — plus the
 * two things only an end-to-end run can show: that every input feature survives
 * into the attribute columns, and that the texture chain (register, resolve,
 * atlas, write) actually reaches disk.
 */
class SyntheticSceneI3SExportTest extends AbstractSyntheticSceneExportTest {

    private static Path layerDir(Path sceneDir) {
        return sceneDir.resolve("layers").resolve("0");
    }

    private static List<Path> nodeDirs(Path layerDir) throws IOException {
        try (Stream<Path> nodes = Files.list(layerDir.resolve("nodes"))) {
            return nodes.filter(Files::isDirectory).sorted().toList();
        }
    }

    @Test
    void writesSceneLayerWithNodePagesAndResources(@TempDir Path tempDir) throws Exception {
        I3SFormatOptions formatOptions = new I3SFormatOptions();
        formatOptions.setGridEdgeLength(GRID_EDGE_LENGTH);
        Path sceneDir = runExport(tempDir, new I3SAdapter(), formatOptions, ".i3s");

        Path layerDir = layerDir(sceneDir);
        Path descriptor = layerDir.resolve("index.json");
        assertTrue(Files.isRegularFile(descriptor), "Missing scene layer descriptor: " + descriptor);

        JSONObject layer = JSON.parseObject(Files.readString(descriptor));
        JSONObject store = layer.getJSONObject("store");
        assertTrue(store != null, "Descriptor is not a valid I3S 3dSceneLayer (no 'store').");
        assertEquals("1.9", store.getString("version"), "I3S store version must stay 1.9.");
        assertEquals(64, layer.getJSONObject("nodePages").getIntValue("nodesPerPage"),
                "nodesPerPage must match the page size the writer actually pages by.");

        // The scene spans ~220 m at a 50 m grid edge, so partitioning must place
        // the four textured buildings in four separate cells. Collapsing the
        // grid to 1x1 still yields two nodes via the mixed-texture split, so the
        // bound has to come from the fixture layout to be worth anything.
        List<Path> nodes = nodeDirs(layerDir);
        long meshNodes = nodes.stream()
                .filter(node -> isNonEmpty(node.resolve("geometries").resolve("0")))
                .count();
        assertTrue(meshNodes >= MIN_MESH_NODES,
                "Only " + meshNodes + " nodes carry geometry; the " + TEXTURED_BUILDINGS
                        + " textured buildings sit in as many separate grid cells, so "
                        + "partitioning must produce at least that many leaves.");

        // Every node the writer created must be described by a node page, or
        // clients cannot reach it.
        Path nodePage = layerDir.resolve("nodepages").resolve("0").resolve("index.json");
        assertTrue(Files.isRegularFile(nodePage), "Missing node page: " + nodePage);
        JSONArray pagedNodes = JSON.parseObject(Files.readString(nodePage)).getJSONArray("nodes");
        assertTrue(pagedNodes != null && pagedNodes.size() >= nodes.size(),
                "Node page lists " + (pagedNodes == null ? 0 : pagedNodes.size())
                        + " nodes but " + nodes.size() + " node directories were written.");

        // The picking contract: features/0/index.json must use the
        // {featureData, geometryData} schema or the JS SDK silently drops the
        // whole pick pipeline. Pinned as a unit in I3SJsonSerializerTest; here
        // we check the writer actually emits the file for every mesh node.
        for (Path node : nodes) {
            Path features = node.resolve("features").resolve("0").resolve("index.json");
            if (!Files.exists(features)) {
                continue;
            }
            assertEquals(Set.of("featureData", "geometryData"),
                    JSON.parseObject(Files.readString(features)).keySet(),
                    "Unexpected feature-document schema in " + features);
        }
    }

    @Test
    void writesTexturesForTexturedNodes(@TempDir Path tempDir) throws Exception {
        I3SFormatOptions formatOptions = new I3SFormatOptions();
        formatOptions.setGridEdgeLength(GRID_EDGE_LENGTH);
        Path sceneDir = runExport(tempDir, new I3SAdapter(), formatOptions, ".i3s");

        // The scene mixes textured and untextured buildings in one cell, so the
        // mixed-node split must yield at least one textured node with a written
        // atlas. A silent texture-resolution failure degrades to untextured
        // output without erroring, which is exactly what this guards.
        List<Path> texturedNodes = new ArrayList<>();
        for (Path node : nodeDirs(layerDir(sceneDir))) {
            Path textures = node.resolve("textures");
            if (Files.isDirectory(textures)) {
                try (Stream<Path> files = Files.list(textures)) {
                    if (files.anyMatch(SyntheticSceneI3SExportTest::isNonEmpty)) {
                        texturedNodes.add(node);
                    }
                }
            }
        }
        assertFalse(texturedNodes.isEmpty(),
                "No node carries a written texture although the scene is textured — "
                        + "the atlas chain fell back to untextured rendering.");
    }

    @Test
    void everyInputFeatureReachesTheAttributeColumns(@TempDir Path tempDir) throws Exception {
        I3SFormatOptions formatOptions = new I3SFormatOptions();
        formatOptions.setGridEdgeLength(GRID_EDGE_LENGTH);
        Path sceneDir = runExport(tempDir, new I3SAdapter(), formatOptions, ".i3s");

        Path layerDir = layerDir(sceneDir);
        // Locate the OBJECTID column by name rather than by its fixed index, so
        // this stays honest if the schema head is ever reordered.
        JSONArray fields = JSON.parseObject(Files.readString(layerDir.resolve("index.json")))
                .getJSONArray("attributeStorageInfo");
        assertTrue(fields != null && !fields.isEmpty(), "Layer declares no attributeStorageInfo.");
        int objectIdColumn = -1;
        for (int i = 0; i < fields.size(); i++) {
            if ("OBJECTID".equals(fields.getJSONObject(i).getString("name"))) {
                objectIdColumn = i;
                break;
            }
        }
        assertTrue(objectIdColumn >= 0, "No OBJECTID column in attributeStorageInfo.");

        Set<String> written = new HashSet<>();
        for (Path node : nodeDirs(layerDir)) {
            Path column = node.resolve("attributes").resolve("f_" + objectIdColumn).resolve("0");
            if (Files.isRegularFile(column)) {
                written.addAll(readStringColumn(column));
            }
        }

        // Nine features in, nine object ids out: implicit-geometry instances are
        // baked into their own meshes here (I3S has no instancing path), so a
        // dropped tree or a mis-split mixed node shows up as a missing id.
        Set<String> expected = new HashSet<>(List.of(
                "building_tex_0", "building_tex_1", "building_tex_2", "building_tex_3",
                "building_mat_0", "building_mat_1", "tree_0", "tree_1", "tree_2"));
        assertEquals(FEATURE_COUNT, expected.size(), "Fixture id list is out of sync.");
        assertEquals(expected, written,
                "The OBJECTID columns do not round-trip the input features exactly.");
    }

    /**
     * Decode an I3S string attribute column: {@code count}, {@code totalBytes},
     * {@code count} per-entry byte counts, then the NUL-terminated UTF-8 values
     * (the byte count includes the terminator).
     */
    private static List<String> readStringColumn(Path column) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(Files.readAllBytes(column)).order(ByteOrder.LITTLE_ENDIAN);
        int count = buffer.getInt();
        buffer.getInt(); // total byte count of the value block
        int[] byteCounts = new int[count];
        for (int i = 0; i < count; i++) {
            byteCounts[i] = buffer.getInt();
        }

        List<String> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            byte[] utf8 = new byte[byteCounts[i] - 1]; // drop the NUL terminator
            buffer.get(utf8);
            assertEquals(0, buffer.get(), "String value " + i + " of " + column + " is not NUL-terminated.");
            values.add(new String(utf8, StandardCharsets.UTF_8));
        }
        return values;
    }

    private static boolean isNonEmpty(Path file) {
        try {
            return Files.isRegularFile(file) && Files.size(file) > 0;
        } catch (IOException e) {
            return false;
        }
    }
}
