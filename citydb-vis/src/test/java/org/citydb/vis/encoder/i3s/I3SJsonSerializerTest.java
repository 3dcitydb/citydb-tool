/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.encoder.i3s;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.citydb.vis.model.AttrField;
import org.citydb.vis.model.AttrType;
import org.citydb.vis.model.FeatureData;
import org.citydb.vis.scene.SceneNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the node-level JSON resources whose exact shape is consumer-critical:
 * {@code features/0/index.json} must use the {@code {featureData,
 * geometryData}} schema (the ArcGIS Maps SDK for JavaScript silently drops
 * its picking pipeline on any other shape) with per-feature mbb/centroid for
 * the pick BVH, and {@code sharedResource} must exist with a non-empty
 * {@code materialDefinitions} map (Esri's SLPK validator rejects both a
 * missing file and an empty object).
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

        JSONObject doc = JSONObject.parseObject(Files.readString(tempDir.resolve("nodes")
                .resolve("3").resolve("features").resolve("0").resolve("index.json")));

        // The JS SDK requires exactly this schema to enable feature picking.
        assertEquals(Set.of("featureData", "geometryData"), doc.keySet());
        JSONArray geometryData = doc.getJSONArray("geometryData");
        assertEquals(1, geometryData.size());
        assertEquals("./geometries/0", geometryData.getJSONObject(0).getString("href"));

        JSONArray featureData = doc.getJSONArray("featureData");
        assertEquals(2, featureData.size());
        JSONObject first = featureData.getJSONObject(0);
        assertEquals(7L, first.getLongValue("id"));
        // position is the mbb centroid; mbb is the feature's exact AABB —
        // distinct per feature, or the pick BVH collapses.
        JSONArray position = first.getJSONArray("position");
        assertEquals(8.15, position.getDoubleValue(0), 1e-12);
        assertEquals(48.75, position.getDoubleValue(1), 1e-12);
        assertEquals(20.0, position.getDoubleValue(2), 1e-12);
        JSONArray mbb = first.getJSONArray("mbb");
        assertEquals(6, mbb.size());
        assertEquals(8.1, mbb.getDoubleValue(0), 1e-12);
        assertEquals(30.0, mbb.getDoubleValue(5), 1e-12);
        assertEquals(9L, featureData.getJSONObject(1).getLongValue("id"));
    }

    @Test
    void sharedResourcePlaceholderCarriesNonEmptyMaterialDefinitions(@TempDir Path tempDir)
            throws IOException {
        new I3SJsonSerializer().writeNodeSharedResource(tempDir, new SceneNode(3, 1));

        JSONObject doc = JSONObject.parseObject(Files.readString(tempDir.resolve("nodes")
                .resolve("3").resolve("shared").resolve("sharedResource").resolve("index.json")));

        // Esri's validator rejects an empty materialDefinitions object.
        JSONObject materials = doc.getJSONObject("materialDefinitions");
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
            assertNotNull(JSONObject.parseObject(Files.readString(statsFile)));
        }
    }
}
