/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.encoder.tiles3d;

import org.citydb.core.json.Json;
import org.citydb.vis.scene.BoundingVolume;
import org.citydb.vis.scene.SceneNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the per-level tileset split the Cesium runtime navigates blindly:
 * every node with children becomes its own subtree JSON (uniform two-level
 * shape), childless mesh leaves stay inline with {@code ../}-prefixed GLB
 * URIs, refine is REPLACE everywhere, leaves carry geometricError 0, and the
 * per-cell ENU-to-ECEF transform appears exactly once — on the cell root's
 * inline representation, never on its external-ref view (double application
 * would displace the whole cell).
 */
class TilesetSerializerTest {
    private static final double GE_RATIO = 0.5;

    // Scene tree under test (indices in parens, tile paths in brackets):
    //   globalRoot(0)
    //     aggRoot(1) [0]
    //       leafCell(2) [0,0]      — childless mesh node, has cell transform
    //       splitCell(3) [0,1]     — cell root with mixed-split children
    //         texChild(4) [0,1,0]  — mesh leaf
    //         untexChild(5) [0,1,1] — mesh leaf
    private SceneNode globalRoot;
    private SceneNode aggRoot;
    private SceneNode leafCell;
    private SceneNode splitCell;
    private Map<Integer, int[]> tilePaths;
    private final double[] leafCellTransform = identityWith(10.0);
    private final double[] splitCellTransform = identityWith(20.0);

    @Test
    void rootTilesetReferencesAggregationSubtree(@TempDir Path tempDir) throws IOException {
        buildTree();
        new TilesetSerializer().writeRootTileset(tempDir, globalRoot, aggRoot,
                new double[]{8.0, 48.0, 0.0, 9.0, 49.0, 100.0}, List.of(), tilePaths, GE_RATIO);

        JsonNode tileset = Json.readObject(
                Files.readString(tempDir.resolve("tileset.json")));
        assertEquals("1.1", tileset.get("asset").path("version").asString(null));

        // Root geometricError = globalRoot radius × ratio, > 0 so Cesium
        // refines into the subtree child instead of rendering null content.
        double rootGeo = tileset.path("geometricError").asDouble(0);
        assertEquals(globalRoot.getBoundingVolume().getRadius() * GE_RATIO, rootGeo, 1e-9);
        assertTrue(rootGeo > 0);

        JsonNode root = tileset.get("root");
        assertEquals("REPLACE", root.path("refine").asString(null));
        JsonNode children = root.get("children");
        assertEquals(1, children.size());
        assertEquals("subtrees/0.json",
                children.get(0).get("content").path("uri").asString(null));
    }

    @Test
    void everyIntermediateNodeGetsItsOwnSubtreeFile(@TempDir Path tempDir) throws IOException {
        buildTree();
        AtomicInteger count = new AtomicInteger();
        Path subtreesDir = tempDir.resolve("subtrees");

        new TilesetSerializer().writeSubTileset(subtreesDir, aggRoot,
                Set.of(2, 4, 5), tilePaths, cellTransforms(), count, GE_RATIO);

        // aggRoot [0] and splitCell [0,1] have children → two subtree files;
        // mesh leaves stay inline.
        assertEquals(2, count.get());
        assertTrue(Files.isRegularFile(subtreesDir.resolve("0.json")));
        assertTrue(Files.isRegularFile(subtreesDir.resolve("0").resolve("1.json")));
        assertFalse(Files.exists(subtreesDir.resolve("0").resolve("0.json")),
                "childless mesh leaf must stay inline, not become a subtree file");
    }

    @Test
    void meshLeavesAreInlinedWithUpNavigatingGlbUris(@TempDir Path tempDir) throws IOException {
        buildTree();
        Path subtreesDir = tempDir.resolve("subtrees");
        new TilesetSerializer().writeSubTileset(subtreesDir, aggRoot,
                Set.of(2, 4, 5), tilePaths, cellTransforms(), new AtomicInteger(), GE_RATIO);

        // subtrees/0.json: aggRoot inline, leafCell inline with GLB one level
        // up, splitCell as external ref.
        JsonNode aggTileset = Json.readObject(
                Files.readString(subtreesDir.resolve("0.json")));
        JsonNode aggTile = aggTileset.get("root");
        assertEquals("REPLACE", aggTile.path("refine").asString(null));
        JsonNode aggChildren = aggTile.get("children");
        assertEquals(2, aggChildren.size());

        JsonNode leafTile = aggChildren.get(0);
        assertEquals("../tiles/0/0.glb", leafTile.get("content").path("uri").asString(null));
        assertEquals(0.0, leafTile.path("geometricError").asDouble(0),
                "mesh leaves must carry geometricError 0");

        JsonNode splitRef = aggChildren.get(1);
        assertEquals("0/1.json", splitRef.get("content").path("uri").asString(null));
        assertNull(splitRef.get("children"),
                "external refs must not inline the subtree's children");

        // subtrees/0/1.json: splitCell inline with its two mesh leaves, GLB
        // URIs navigating two levels up.
        JsonNode splitTileset = Json.readObject(
                Files.readString(subtreesDir.resolve("0").resolve("1.json")));
        JsonNode splitChildren = splitTileset.get("root").get("children");
        assertEquals(2, splitChildren.size());
        assertEquals("../../tiles/0/1/0.glb",
                splitChildren.get(0).get("content").path("uri").asString(null));
        assertEquals("../../tiles/0/1/1.glb",
                splitChildren.get(1).get("content").path("uri").asString(null));

        // Intermediate geometricError = R × ratio; propagated identically to
        // the external-ref view and the inline root of the subtree file.
        double expected = splitCell.getBoundingVolume().getRadius() * GE_RATIO;
        assertEquals(expected, splitRef.path("geometricError").asDouble(0), 1e-9);
        assertEquals(expected,
                splitTileset.get("root").path("geometricError").asDouble(0), 1e-9);
    }

    @Test
    void cellTransformAppearsOnlyOnTheInlineRepresentation(@TempDir Path tempDir)
            throws IOException {
        buildTree();
        Path subtreesDir = tempDir.resolve("subtrees");
        new TilesetSerializer().writeSubTileset(subtreesDir, aggRoot,
                Set.of(2, 4, 5), tilePaths, cellTransforms(), new AtomicInteger(), GE_RATIO);

        JsonNode aggTileset = Json.readObject(
                Files.readString(subtreesDir.resolve("0.json")));
        JsonNode aggChildren = aggTileset.get("root").get("children");

        // Inline leaf cell carries its transform.
        JsonNode leafTransform = aggChildren.get(0).get("transform");
        assertNotNull(leafTransform);
        assertEquals(10.0, leafTransform.path(12).asDouble(0), 1e-12);

        // External-ref view of the split cell must stay identity (absent):
        // the transform is applied inside the referenced subtree file instead.
        assertNull(aggChildren.get(1).get("transform"),
                "transform on the external ref would be applied twice");
        JsonNode splitTileset = Json.readObject(
                Files.readString(subtreesDir.resolve("0").resolve("1.json")));
        JsonNode splitTransform = splitTileset.get("root").get("transform");
        assertNotNull(splitTransform);
        assertEquals(20.0, splitTransform.path(12).asDouble(0), 1e-12);
    }

    // ---- fixture -----------------------------------------------------------

    private void buildTree() {
        globalRoot = new SceneNode(0, 0);
        aggRoot = new SceneNode(1, 0);
        leafCell = new SceneNode(2, 1);
        splitCell = new SceneNode(3, 1);
        SceneNode texChild = new SceneNode(4, 2);
        SceneNode untexChild = new SceneNode(5, 2);

        BoundingVolume cellBox = BoundingVolume.ofBoundingBox(8.0, 48.0, 0.0, 8.5, 48.5, 50.0);
        BoundingVolume otherBox = BoundingVolume.ofBoundingBox(8.5, 48.5, 0.0, 9.0, 49.0, 50.0);
        leafCell.setBoundingVolume(cellBox);
        splitCell.setBoundingVolume(otherBox);
        texChild.setBoundingVolume(otherBox);
        untexChild.setBoundingVolume(otherBox);

        splitCell.addChild(texChild);
        splitCell.addChild(untexChild);
        aggRoot.addChild(leafCell);
        aggRoot.addChild(splitCell);
        aggRoot.updateBoundingVolume();
        globalRoot.addChild(aggRoot);
        globalRoot.updateBoundingVolume();

        tilePaths = TilePaths.buildPathIndex(aggRoot);
    }

    private Map<Integer, double[]> cellTransforms() {
        return Map.of(2, leafCellTransform, 3, splitCellTransform);
    }

    /** Column-major identity with a recognizable X translation in slot 12. */
    private static double[] identityWith(double tx) {
        double[] m = new double[16];
        m[0] = m[5] = m[10] = m[15] = 1.0;
        m[12] = tx;
        return m;
    }
}
