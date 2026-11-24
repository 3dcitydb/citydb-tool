/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigManagerTest {
    @TempDir
    Path tempDir;

    public static class Options {
        private String lineage;
    }

    @Test
    public void byteOrderMarkIsIgnored() throws Exception {
        Path file = tempDir.resolve("bom.json");
        byte[] content = "{\"options\": {\"lineage\": \"x\"}}".getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[content.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(content, 0, withBom, 3, content.length);
        Files.write(file, withBom);

        Config config = ConfigManager.newInstance().read(file, Config.class);
        assertEquals("x", config.get("options", Options.class).lineage);
    }

    @Test
    public void invalidUtf8IsReplaced() throws Exception {
        Path file = tempDir.resolve("latin1.json");
        Files.write(file, "{\"options\": {\"lineage\": \"Straße\"}}".getBytes(StandardCharsets.ISO_8859_1));

        Config config = ConfigManager.newInstance().read(file, Config.class);
        String lineage = config.get("options", Options.class).lineage;
        assertTrue(lineage.startsWith("Stra") && lineage.endsWith("e"), lineage);
    }
}
