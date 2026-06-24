/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.integration;

import org.citydb.vis.I3SAdapter;
import org.citydb.vis.config.I3SFormatOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end smoke test for the I3S writer driven by the real DB export
 * pipeline (see {@link AbstractRailwaySceneExportIT}). Asserts the OGC I3S
 * scene-layer structure: a {@code layers/0/index.json} descriptor, a populated
 * {@code nodes/} directory, and at least one written texture.
 */
class RailwaySceneI3SExportIT extends AbstractRailwaySceneExportIT {

    @Test
    void exportsAllFeaturesToI3SLayer(@TempDir Path tempDir) throws Exception {
        Path sceneDir = runExport(tempDir, new I3SAdapter(), new I3SFormatOptions(), ".i3s");

        // The I3S writer writes the scene layer under <scene>/layers/0. Assert
        // the descriptor exists and is valid I3S JSON (a "store" object is
        // mandatory in a 3dSceneLayer descriptor).
        Path layerDir = sceneDir.resolve("layers").resolve("0");
        Path descriptor = layerDir.resolve("index.json");
        assertTrue(Files.isRegularFile(descriptor),
                "Missing I3S scene layer descriptor: " + descriptor);
        assertTrue(com.alibaba.fastjson2.JSON.parseObject(Files.readString(descriptor))
                        .containsKey("store"),
                "Scene layer descriptor is not a valid I3S 3dSceneLayer (no 'store').");

        Path nodesDir = layerDir.resolve("nodes");
        assertTrue(Files.isDirectory(nodesDir),
                "Missing I3S nodes directory: " + nodesDir);
        try (var nodes = Files.list(nodesDir)) {
            assertTrue(nodes.findAny().isPresent(), "I3S nodes directory is empty.");
        }

        // The railway scene is textured, so at least one node must carry a
        // written texture (nodes/<i>/textures/0.<ext>). This guards the
        // exporter-output-file wiring in the base harness: without it the DB
        // exporter writes no texture files, TextureStore can't resolve them, and
        // every node silently falls back to untextured rendering.
        try (var nodeDirs = Files.list(nodesDir)) {
            boolean anyTexture = nodeDirs
                    .map(node -> node.resolve("textures"))
                    .filter(Files::isDirectory)
                    .anyMatch(texDir -> {
                        try (var files = Files.list(texDir)) {
                            return files.findAny().isPresent();
                        } catch (IOException e) {
                            return false;
                        }
                    });
            assertTrue(anyTexture, "No node carries a written texture — textures failed to load "
                    + "(check exportOptions.setOutputFile wiring).");
        }
    }
}
