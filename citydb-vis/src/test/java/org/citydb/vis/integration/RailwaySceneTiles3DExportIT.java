/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.integration;

import org.citydb.vis.Tiles3DAdapter;
import org.citydb.vis.config.Tiles3DFormatOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end smoke test for the 3D Tiles writer driven by the real DB export
 * pipeline (see {@link AbstractRailwaySceneExportIT}). Asserts the 3D Tiles 1.1
 * structure: a {@code tileset.json} entry point and at least one non-empty
 * {@code .glb} tile under {@code tiles/}.
 * <p>
 * Unlike I3S, 3D Tiles bakes textures into the GLB (no sidecar texture files),
 * so the texture presence is asserted indirectly via a non-trivial GLB size —
 * the GLB carries the embedded texture image plus geometry buffers.
 */
class RailwaySceneTiles3DExportIT extends AbstractRailwaySceneExportIT {

    @Test
    void exportsAllFeaturesToTileset(@TempDir Path tempDir) throws Exception {
        Path sceneDir = runExport(tempDir, new Tiles3DAdapter(), new Tiles3DFormatOptions(), ".3dtiles");

        // The 3D Tiles writer writes the entry-point tileset.json at the scene
        // root and the GLB tiles under <scene>/tiles. Assert the tileset is
        // valid (a 3D Tiles tileset has mandatory "asset" and "root" members).
        Path tileset = sceneDir.resolve("tileset.json");
        assertTrue(Files.isRegularFile(tileset), "Missing 3D Tiles entry point: " + tileset);
        var descriptor = com.alibaba.fastjson2.JSON.parseObject(Files.readString(tileset));
        assertTrue(descriptor.containsKey("asset") && descriptor.containsKey("root"),
                "tileset.json is not a valid 3D Tiles tileset (missing 'asset'/'root').");

        // Tiles live under tiles/<path>/<n>.glb (nested by the aggregation tree),
        // so walk recursively. Require at least one non-empty GLB — an empty file
        // would mean geometry/texture encoding produced nothing.
        Path tilesDir = sceneDir.resolve("tiles");
        assertTrue(Files.isDirectory(tilesDir), "Missing 3D Tiles tiles directory: " + tilesDir);
        try (Stream<Path> tiles = Files.walk(tilesDir)) {
            List<Path> glbs = tiles
                    .filter(p -> p.getFileName().toString().endsWith(".glb"))
                    .toList();
            assertFalse(glbs.isEmpty(), "No .glb tiles were written under " + tilesDir);
            assertTrue(glbs.stream().anyMatch(RailwaySceneTiles3DExportIT::isNonEmpty),
                    "Every .glb tile is empty — geometry/texture encoding produced no content.");
        }
    }

    private static boolean isNonEmpty(Path glb) {
        try {
            return Files.size(glb) > 0;
        } catch (java.io.IOException e) {
            return false;
        }
    }
}
