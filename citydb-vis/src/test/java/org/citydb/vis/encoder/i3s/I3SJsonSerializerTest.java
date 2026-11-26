/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.encoder.i3s;

import org.citydb.core.json.Json;
import org.citydb.vis.model.AttrField;
import org.citydb.vis.model.AttrType;
import org.citydb.vis.model.FeatureData;
import org.citydb.vis.scene.SceneNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the node-level JSON resources whose exact shape is consumer-critical:
 * {@code features/0/index.json} must use the {@code {featureData,
 * geometryData}} schema (the ArcGIS Maps SDK for JavaScript silently drops
 * its picking pipeline on any other shape) with per-feature mbb/centroid for
 * the pick BVH, and {@code sharedResource} must exist with a non-empty
 * {@code materialDefinitions} map (Esri's SLPK validator rejects both a
 * missing file and an empty object).
 * <p>
 * Node pages are pinned as well: consumers locate a node's page as
 * {@code floor(index / nodesPerPage)} with the {@code nodesPerPage} value
 * advertised in the scene layer descriptor, so the pagination here must
 * stay in lock-step with that constant (64); a {@code mesh} entry may
 * appear exactly for the nodes in {@code meshNodeIndices}; and
 * {@code lodThreshold} must serialize as an integer token — ArcGIS Pro
 * rejects a float value ("Value is not convertible to Int").
 */
class I3SJsonSerializerTest {

    @Test
    void nodeFeatureDocumentUsesPickingSchema(@TempDir Path tempDir) throws IOException {
        List<FeatureData> features = List.of(
                new FeatureData(7L, "BLDG_1", "Building", Map.of()),
                new FeatureData(9L, "BLDG_2", "Building", Map.of()));
        List<double[]> aabbs = List.of(
                new double[]{8.1, 48.7, 10.0, 8.2, 48.8, 30.0},
                new double[]{8.3, 48.6, 0.0, 8.4, 48.7, 12.0});

        new I3SJsonSerializer().writeNodeFeatures(tempDir, new SceneNode(3, 1), features, aabbs);

        JsonNode doc = Json.readObject(Files.readString(tempDir.resolve("nodes")
                .resolve("3").resolve("features").resolve("0").resolve("index.json")));

        // The JS SDK requires exactly this schema to enable feature picking.
        assertEquals(Set.of("featureData", "geometryData"), Set.copyOf(doc.propertyNames()));
        JsonNode geometryData = doc.get("geometryData");
        assertEquals(1, geometryData.size());
        assertEquals("./geometries/0", geometryData.get(0).path("href").asString(null));

        JsonNode featureData = doc.get("featureData");
        assertEquals(2, featureData.size());
        JsonNode first = featureData.get(0);
        assertEquals(7L, first.path("id").asLong(0));
        // position is the mbb centroid; mbb is the feature's exact AABB —
        // distinct per feature, or the pick BVH collapses.
        JsonNode position = first.get("position");
        assertEquals(8.15, position.path(0).asDouble(0), 1e-12);
        assertEquals(48.75, position.path(1).asDouble(0), 1e-12);
        assertEquals(20.0, position.path(2).asDouble(0), 1e-12);
        JsonNode mbb = first.get("mbb");
        assertEquals(6, mbb.size());
        assertEquals(8.1, mbb.path(0).asDouble(0), 1e-12);
        assertEquals(30.0, mbb.path(5).asDouble(0), 1e-12);
        assertEquals(9L, featureData.get(1).path("id").asLong(0));
    }

    @Test
    void sharedResourcePlaceholderCarriesNonEmptyMaterialDefinitions(@TempDir Path tempDir)
            throws IOException {
        new I3SJsonSerializer().writeNodeSharedResource(tempDir, new SceneNode(3, 1));

        JsonNode doc = Json.readObject(Files.readString(tempDir.resolve("nodes")
                .resolve("3").resolve("shared").resolve("sharedResource").resolve("index.json")));

        // Esri's validator rejects an empty materialDefinitions object.
        JsonNode materials = doc.get("materialDefinitions");
        assertNotNull(materials);
        assertFalse(materials.isEmpty(),
                "materialDefinitions must be non-empty for the SLPK validator");
    }

    @Test
    void statisticsAreWrittenForEveryFieldIncludingUnobservedOnes(@TempDir Path tempDir)
            throws IOException {
        List<AttrField> fields = List.of(
                new AttrField("OID", AttrType.OID),
                new AttrField("name", AttrType.STRING));

        // Empty stats map: both fields were declared but never observed —
        // resources must still exist to satisfy the validator's
        // MISSING_ATTRIBUTE_STATS_DECL check.
        new I3SJsonSerializer().writeStatistics(tempDir, fields, Map.of());

        for (int i = 0; i < fields.size(); i++) {
            Path statsFile = tempDir.resolve("statistics")
                    .resolve("f_" + i).resolve("0").resolve("index.json");
            assertTrue(Files.isRegularFile(statsFile), "missing stats resource: " + statsFile);
            assertNotNull(Json.readObject(Files.readString(statsFile)));
        }
    }

    @Test
    void exactlySixtyFourNodesFillASingleNodePage(@TempDir Path tempDir) throws IOException {
        // 64 is deliberately a literal, not I3SConstants.NODES_PER_PAGE: the
        // descriptor advertises the same value and consumers address pages as
        // floor(index / 64), so a silent change of the constant must fail here.
        new I3SJsonSerializer().writeNodePages(tempDir, sceneNodes(64), Set.of(), false);

        JsonNode entries = readNodePage(tempDir, 0).get("nodes");
        assertEquals(64, entries.size());
        assertEquals(0, entries.get(0).path("index").asInt(0));
        assertEquals(63, entries.get(63).path("index").asInt(0));
        assertFalse(Files.exists(tempDir.resolve("nodepages").resolve("1")),
                "the 64th node must not open a second page");
    }

    @Test
    void sixtyFifthNodeSpillsIntoSecondPageWithOneEntry(@TempDir Path tempDir)
            throws IOException {
        new I3SJsonSerializer().writeNodePages(tempDir, sceneNodes(65), Set.of(), false);

        // Page 0 stays full; the subList math must not duplicate node 63/64
        // across the boundary or clip the last page short.
        JsonNode firstPage = readNodePage(tempDir, 0).get("nodes");
        assertEquals(64, firstPage.size());
        assertEquals(63, firstPage.get(63).path("index").asInt(0));

        JsonNode secondPage = readNodePage(tempDir, 1).get("nodes");
        assertEquals(1, secondPage.size());
        assertEquals(64, secondPage.get(0).path("index").asInt(0));

        assertFalse(Files.exists(tempDir.resolve("nodepages").resolve("2")));
    }

    @Test
    void meshEntriesAppearExactlyForMeshNodeIndices(@TempDir Path tempDir) throws IOException {
        List<SceneNode> nodes = sceneNodes(3);
        nodes.get(1).setOutputVertexCount(6).setFeatureCount(2);

        new I3SJsonSerializer().writeNodePages(tempDir, nodes, Set.of(1), false);

        JsonNode entries = readNodePage(tempDir, 0).get("nodes");
        // Only the node listed in meshNodeIndices may carry a mesh — ArcGIS
        // requests geometry for every entry that declares one, so a mesh on
        // an empty node 404s while a missing mesh on a leaf drops geometry.
        assertNull(entries.get(0).get("mesh"));
        assertNull(entries.get(2).get("mesh"));

        JsonNode mesh = entries.get(1).get("mesh");
        assertNotNull(mesh, "mesh node must carry a mesh entry");
        // Geometry/material/attribute resources are the node's own index.
        assertEquals(1, mesh.get("geometry").path("resource").asInt(0));
        assertEquals(6, mesh.get("geometry").path("vertexCount").asInt(0));
        assertEquals(2, mesh.get("geometry").path("featureCount").asInt(0));
        assertEquals(1, mesh.get("material").path("resource").asInt(0));
    }

    @Test
    void lodThresholdSerializesAsIntegerNotFloat(@TempDir Path tempDir) throws IOException {
        List<SceneNode> nodes = sceneNodes(1);
        nodes.get(0).setLodThreshold(9000);

        new I3SJsonSerializer().writeNodePages(tempDir, nodes, Set.of(), false);

        // Assert on the raw JSON text: ArcGIS Pro rejects "9000.0" with
        // "Value is not convertible to Int", so the token itself must be an
        // integer — a parsed-number comparison would not catch a float.
        String raw = Files.readString(tempDir.resolve("nodepages")
                .resolve("0").resolve("index.json"));
        Matcher matcher = Pattern.compile("\"lodThreshold\"\\s*:\\s*([^,\\s}]+)").matcher(raw);
        assertTrue(matcher.find(), "lodThreshold must be serialized");
        assertEquals("9000", matcher.group(1),
                "lodThreshold must serialize as an integer token, not a float");
    }

    // ---- fixture -----------------------------------------------------------

    private static List<SceneNode> sceneNodes(int count) {
        List<SceneNode> nodes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            nodes.add(new SceneNode(i, 1));
        }
        return nodes;
    }

    private static JsonNode readNodePage(Path tempDir, int page) throws IOException {
        Path pageFile = tempDir.resolve("nodepages")
                .resolve(String.valueOf(page)).resolve("index.json");
        assertTrue(Files.isRegularFile(pageFile), "missing node page: " + pageFile);
        return Json.readObject(Files.readString(pageFile));
    }
}
