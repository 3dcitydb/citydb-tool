/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.writer;

import org.citydb.core.json.Json;
import org.citydb.vis.Tiles3DAdapter;
import org.citydb.vis.options.Tiles3DFormatOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Database-free end-to-end smoke test for the 3D Tiles writer (see
 * {@link AbstractSyntheticSceneExportTest}). Asserts the 3D Tiles 1.1 structure
 * (tileset entry point, per-level subtree files, GLB tiles), the per-cell
 * placement invariant, and the two implicit-geometry paths: instanced via
 * {@code EXT_mesh_gpu_instancing} when opted in, baked when not.
 */
class SyntheticSceneTiles3DExportTest extends AbstractSyntheticSceneExportTest {

    private static Tiles3DFormatOptions options() {
        Tiles3DFormatOptions formatOptions = new Tiles3DFormatOptions();
        formatOptions.setGridEdgeLength(GRID_EDGE_LENGTH);
        return formatOptions;
    }

    private static List<Path> glbTiles(Path sceneDir) throws IOException {
        Path tilesDir = sceneDir.resolve("tiles");
        assertTrue(Files.isDirectory(tilesDir), "Missing tiles directory: " + tilesDir);
        try (Stream<Path> tiles = Files.walk(tilesDir)) {
            // Tiles nest by the aggregation tree, so walk rather than list.
            return tiles.filter(p -> p.getFileName().toString().endsWith(".glb")).sorted().toList();
        }
    }

    @Test
    void writesTilesetWithPerCellPlacementAndGlbTiles(@TempDir Path tempDir) throws Exception {
        Path sceneDir = runExport(tempDir, new Tiles3DAdapter(), options(), ".3dtiles");

        Path tileset = sceneDir.resolve("tileset.json");
        assertTrue(Files.isRegularFile(tileset), "Missing 3D Tiles entry point: " + tileset);
        JsonNode descriptor = Json.readObject(Files.readString(tileset));
        assertEquals("1.1", descriptor.get("asset").path("version").asString(null),
                "Tileset must declare 3D Tiles 1.1.");
        assertNotNull(descriptor.get("root"), "Tileset has no root tile.");

        // Per-cell placement invariant: the root stays identity and each cell
        // carries its own ENU-to-ECEF transform, so cells stay tangent to the
        // ellipsoid at their own centre instead of on one dataset-wide plane.
        assertFalse(descriptor.get("root").has("transform"),
                "Root tile must not carry a transform under per-cell placement.");
        try (Stream<Path> subtrees = Files.walk(sceneDir.resolve("subtrees"))) {
            assertTrue(subtrees.filter(p -> p.getFileName().toString().endsWith(".json"))
                            .anyMatch(SyntheticSceneTiles3DExportTest::hasEcefTransform),
                    "No cell tile carried an ENU-to-ECEF transform; per-cell placement is not in effect.");
        }

        List<Path> glbs = glbTiles(sceneDir);
        assertFalse(glbs.isEmpty(), "No .glb tiles were written.");
        for (Path glb : glbs) {
            assertTrue(Files.size(glb) > 0, "Empty .glb tile: " + glb);
        }

        // The six buildings must survive encoding. Aggregation levels re-encode
        // geometry, so this is a floor, not an equality.
        int triangles = glbs.stream().mapToInt(SyntheticSceneTiles3DExportTest::triangleCount).sum();
        assertTrue(triangles >= BUILDING_TRIANGLES,
                "Tiles carry " + triangles + " triangles, fewer than the "
                        + BUILDING_TRIANGLES + " the buildings alone contribute.");
    }

    @Test
    void bakesTextureAtlasesIntoTilesWithinTheSizeCap(@TempDir Path tempDir) throws Exception {
        Tiles3DFormatOptions formatOptions = options();
        Path sceneDir = runExport(tempDir, new Tiles3DAdapter(), formatOptions, ".3dtiles");

        // 3D Tiles embeds textures in the GLB rather than writing sidecar files,
        // so a silent texture-resolution failure shows up as tiles with no
        // images at all.
        boolean anyImage = false;
        for (Path glb : glbTiles(sceneDir)) {
            for (BufferedImage image : embeddedImages(glb)) {
                anyImage = true;
                // Node atlas pages and instanced prototype atlas pages alike
                // must obey the user's page cap; a violation means one of the
                // builder paths bypassed the setting.
                assertTrue(image.getWidth() <= formatOptions.getMaxAtlasSize()
                                && image.getHeight() <= formatOptions.getMaxAtlasSize(),
                        "Embedded image in " + glb + " is " + image.getWidth() + "x"
                                + image.getHeight() + ", exceeding the atlas cap "
                                + formatOptions.getMaxAtlasSize() + ".");
            }
        }
        assertTrue(anyImage, "No tile embeds a texture although the scene is textured.");
    }

    @Test
    void placesImplicitGeometriesViaInstancingWhenEnabled(@TempDir Path tempDir) throws Exception {
        Path sceneDir = runExport(tempDir, new Tiles3DAdapter(),
                options().setImplicitGeometryInstancing(true), ".3dtiles");

        int instances = 0;
        for (Path glb : glbTiles(sceneDir)) {
            instances += instanceCount(gltfJson(glb));
        }
        // One shared template, three placements: every tree must be placed by
        // the instancing path. Aggregation may re-emit an instanced node at a
        // coarser level, so this is a floor.
        assertTrue(instances >= TREES,
                "Only " + instances + " of " + TREES + " implicit-geometry instances were "
                        + "placed via EXT_mesh_gpu_instancing — the instancing path fell back to baking.");
    }

    @Test
    void bakesImplicitGeometriesByDefault(@TempDir Path tempDir) throws Exception {
        // Instancing is opt-in (--implicit-geometry-instancing); without it every
        // tree must be materialized into world-space geometry instead.
        Path sceneDir = runExport(tempDir, new Tiles3DAdapter(), options(), ".3dtiles");

        List<Path> glbs = glbTiles(sceneDir);
        for (Path glb : glbs) {
            assertFalse(gltfJson(glb).toString().contains("EXT_mesh_gpu_instancing"),
                    "Instancing is off by default, but " + glb + " declares EXT_mesh_gpu_instancing.");
        }

        // Baking is observable in the triangle budget: three trees of twelve
        // triangles each land in the tiles on top of the buildings.
        int triangles = glbs.stream().mapToInt(SyntheticSceneTiles3DExportTest::triangleCount).sum();
        assertTrue(triangles >= BUILDING_TRIANGLES + TREES * TREE_TEMPLATE_TRIANGLES,
                "Tiles carry " + triangles + " triangles — too few for " + TREES
                        + " baked trees on top of the buildings.");
    }

    @Test
    void emitsPrimitiveOutlinesWhenEnabled(@TempDir Path tempDir) throws Exception {
        Path sceneDir = runExport(tempDir, new Tiles3DAdapter(),
                options().setEnableOutline(true), ".3dtiles");

        assertTrue(glbTiles(sceneDir).stream().anyMatch(glb ->
                        gltfJson(glb).toString().contains("CESIUM_primitive_outline")),
                "No tile declares CESIUM_primitive_outline although outline emission is enabled.");
    }

    // ---- GLB helpers --------------------------------------------------------

    /**
     * Parse a GLB's JSON chunk. Reads only the chunk, not the whole file —
     * textured tiles carry multi-MB embedded atlases.
     */
    private static JsonNode gltfJson(Path glb) {
        try {
            byte[] bytes = Files.readAllBytes(glb);
            int jsonLength = readIntLE(bytes, 12);
            return Json.readObject(new String(bytes, 20, jsonLength, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new AssertionError("Failed to read " + glb, e);
        }
    }

    /** Triangles across all mesh primitives, from the index (or position) accessor. */
    private static int triangleCount(Path glb) {
        JsonNode root = gltfJson(glb);
        JsonNode meshes = root.get("meshes");
        JsonNode accessors = root.get("accessors");
        if (meshes == null || accessors == null) {
            return 0;
        }

        int triangles = 0;
        for (int i = 0; i < meshes.size(); i++) {
            JsonNode primitives = meshes.get(i).get("primitives");
            for (int p = 0; p < primitives.size(); p++) {
                JsonNode primitive = primitives.get(p);
                // Mode 4 (TRIANGLES) is the glTF default and the only mode the
                // encoder emits; anything else would invalidate this count.
                assertEquals(4, primitive.path("mode").asInt(4),
                        "Non-triangle primitive in " + glb);
                Integer indices = Json.getInteger(primitive, "indices");
                int vertexOrIndexCount = indices != null
                        ? accessors.get(indices).path("count").asInt(0)
                        : accessors.get(primitive.get("attributes")
                        .path("POSITION").asInt(0)).path("count").asInt(0);
                triangles += vertexOrIndexCount / 3;
            }
        }
        return triangles;
    }

    /** Total {@code EXT_mesh_gpu_instancing} placements declared by the tile's nodes. */
    private static int instanceCount(JsonNode root) {
        JsonNode nodes = root.get("nodes");
        JsonNode accessors = root.get("accessors");
        if (nodes == null || accessors == null) {
            return 0;
        }

        int instances = 0;
        for (int i = 0; i < nodes.size(); i++) {
            JsonNode extensions = nodes.get(i).get("extensions");
            JsonNode instancing = extensions != null
                    ? extensions.get("EXT_mesh_gpu_instancing")
                    : null;
            if (instancing != null) {
                // TRANSLATION is mandatory for the extension, so its accessor
                // count is the placement count.
                instances += accessors
                        .get(instancing.get("attributes").path("TRANSLATION").asInt(0))
                        .path("count").asInt(0);
            }
        }
        return instances;
    }

    /** Decode every image embedded in the GLB's BIN chunk via its bufferView. */
    private static List<BufferedImage> embeddedImages(Path glb) throws IOException {
        byte[] bytes = Files.readAllBytes(glb);
        int jsonLength = readIntLE(bytes, 12);
        JsonNode root = Json.readObject(new String(bytes, 20, jsonLength, StandardCharsets.UTF_8));
        JsonNode images = root.get("images");
        if (images == null) {
            return List.of();
        }

        JsonNode bufferViews = root.get("bufferViews");
        int binStart = 20 + jsonLength + 8; // skip the BIN chunk's own header
        List<BufferedImage> decoded = new ArrayList<>(images.size());
        for (int i = 0; i < images.size(); i++) {
            Integer view = Json.getInteger(images.get(i), "bufferView");
            if (view == null) {
                continue;
            }
            JsonNode bufferView = bufferViews.get(view);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes,
                    binStart + bufferView.path("byteOffset").asInt(0),
                    bufferView.path("byteLength").asInt(0)));
            assertNotNull(image, "Embedded image " + i + " of " + glb + " failed to decode.");
            decoded.add(image);
        }
        return decoded;
    }

    private static int readIntLE(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16) | ((bytes[offset + 3] & 0xff) << 24);
    }

    /**
     * True if any tile in the subtree JSON carries a 16-element transform whose
     * translation column sits at Earth-radius magnitude — a genuine ENU-to-ECEF
     * placement rather than a small local nudge.
     */
    private static boolean hasEcefTransform(Path subtreeJson) {
        try {
            return tileHasEcefTransform(
                    Json.readObject(Files.readString(subtreeJson)).get("root"));
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean tileHasEcefTransform(JsonNode tile) {
        if (tile == null) {
            return false;
        }
        JsonNode transform = tile.get("transform");
        if (transform != null && transform.size() == 16) {
            double tx = transform.path(12).asDouble(0);
            double ty = transform.path(13).asDouble(0);
            double tz = transform.path(14).asDouble(0);
            double radius = Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (radius > 6.0e6 && radius < 7.0e6) {
                return true;
            }
        }

        JsonNode children = tile.get("children");
        if (children != null) {
            for (int i = 0; i < children.size(); i++) {
                if (tileHasEcefTransform(children.get(i))) {
                    return true;
                }
            }
        }
        return false;
    }
}
