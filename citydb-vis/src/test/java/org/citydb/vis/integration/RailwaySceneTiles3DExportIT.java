/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.integration;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.citydb.vis.Tiles3DAdapter;
import org.citydb.vis.config.Tiles3DFormatOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
        Path sceneDir = runExport(tempDir, new Tiles3DAdapter(),
                new Tiles3DFormatOptions().setImplicitGeometryInstancing(true), ".3dtiles");

        // The 3D Tiles writer writes the entry-point tileset.json at the scene
        // root and the GLB tiles under <scene>/tiles. Assert the tileset is
        // valid (a 3D Tiles tileset has mandatory "asset" and "root" members).
        Path tileset = sceneDir.resolve("tileset.json");
        assertTrue(Files.isRegularFile(tileset), "Missing 3D Tiles entry point: " + tileset);
        var descriptor = com.alibaba.fastjson2.JSON.parseObject(Files.readString(tileset));
        assertTrue(descriptor.containsKey("asset") && descriptor.containsKey("root"),
                "tileset.json is not a valid 3D Tiles tileset (missing 'asset'/'root').");

        // Per-cell placement invariant: the root tile must be identity (no
        // transform) — global ECEF placement now happens per cell so cells stay
        // tangent to the ellipsoid at their own centers rather than on one
        // dataset-wide plane (which floats far cells by the curvature sagitta).
        assertFalse(descriptor.getJSONObject("root").containsKey("transform"),
                "Root tile must not carry a transform under per-cell placement.");

        // At least one tile (a cell root, in a subtree file) must carry a real
        // ENU-to-ECEF transform: a 16-element column-major matrix whose
        // translation (elements 12..14) sits at ECEF magnitude (~Earth radius).
        try (Stream<Path> subtrees = Files.walk(sceneDir.resolve("subtrees"))) {
            boolean anyEcefTransform = subtrees
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .anyMatch(RailwaySceneTiles3DExportIT::hasEcefTransform);
            assertTrue(anyEcefTransform,
                    "No cell tile carried an ENU-to-ECEF transform; per-cell placement is not in effect.");
        }

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

            // The railway scene carries 15 implicit-geometry instances of 3
            // templates (SolitaryVegetationObject trees). With GPU instancing
            // enabled on the format options, at least one GLB must place them
            // via EXT_mesh_gpu_instancing — zero instanced tiles would mean
            // every instance silently fell back to the baked path.
            assertTrue(glbs.stream().anyMatch(RailwaySceneTiles3DExportIT::hasInstancingMarker),
                    "No .glb carries EXT_mesh_gpu_instancing although the scene contains " +
                            "implicit geometries — the instancing path fell back to baking.");

            // Optional debug hand-off: when VIS_IT_KEEP_SCENE_DIR is set, copy
            // the whole scene there so it can be inspected / viewed after the
            // @TempDir is wiped.
            String keepDir = System.getenv("VIS_IT_KEEP_SCENE_DIR");
            if (keepDir != null && !keepDir.isBlank()) {
                copyTree(sceneDir, Path.of(keepDir));
            }
        }
    }

    /**
     * Whether the GLB declares {@code EXT_mesh_gpu_instancing}. Reads only the
     * JSON chunk (12-byte GLB header + 8-byte chunk header + chunk length),
     * not the whole file — textured tiles carry multi-MB embedded atlases.
     */
    private static boolean hasInstancingMarker(Path glb) {
        try (InputStream in = Files.newInputStream(glb);
             DataInputStream data = new DataInputStream(in)) {
            // readFully (not skipBytes, which may skip short) for the 12-byte
            // GLB header and the 4-byte chunk type; bound-check the chunk
            // length so a corrupt file yields false rather than a
            // NegativeArraySizeException/OOME escaping the IOException catch.
            data.readFully(new byte[12]); // magic + version + total length
            int jsonLength = Integer.reverseBytes(data.readInt()); // little-endian
            data.readFully(new byte[4]); // chunk type
            if (jsonLength <= 0 || jsonLength > Files.size(glb)) {
                return false;
            }
            byte[] json = new byte[jsonLength];
            data.readFully(json);
            return new String(json, StandardCharsets.UTF_8).contains("EXT_mesh_gpu_instancing");
        } catch (IOException e) {
            return false;
        }
    }

    private static void copyTree(Path source, Path target) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path p : paths.toList()) {
                Path dest = target.resolve(source.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /**
     * True if any tile in the given subtree JSON carries a 16-element transform
     * whose translation column sits at Earth-radius magnitude — i.e. a genuine
     * ENU-to-ECEF placement rather than a small local nudge.
     */
    private static boolean hasEcefTransform(Path subtreeJson) {
        try {
            JSONObject descriptor = com.alibaba.fastjson2.JSON.parseObject(Files.readString(subtreeJson));
            return tileHasEcefTransform(descriptor.getJSONObject("root"));
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean tileHasEcefTransform(JSONObject tile) {
        if (tile == null) {
            return false;
        }
        JSONArray t = tile.getJSONArray("transform");
        if (t != null && t.size() == 16) {
            double tx = t.getDoubleValue(12), ty = t.getDoubleValue(13), tz = t.getDoubleValue(14);
            double r = Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (r > 6.0e6 && r < 7.0e6) {
                return true;
            }
        }
        JSONArray children = tile.getJSONArray("children");
        if (children != null) {
            for (int i = 0; i < children.size(); i++) {
                if (tileHasEcefTransform(children.getJSONObject(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isNonEmpty(Path glb) {
        try {
            return Files.size(glb) > 0;
        } catch (IOException e) {
            return false;
        }
    }
}
