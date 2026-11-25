/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.config.common;

import org.citydb.config.Config;
import org.citydb.config.ConfigException;
import org.citydb.config.ConfigManager;
import org.citydb.config.SerializableConfig;
import org.citydb.core.json.JsonMapping;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigObjectTest {
    private static final String INPUT = """
            {
              "somePluginOptions": {
                "enabled": true,
                "threshold": 1.50,
                "large": 1e3,
                "count": 12345678901,
                "name": "plugin \\"quoted\\" ä",
                "nested": {"list": [1, 2.5, "x", true, null, {"deep": []}], "empty": {}}
              },
              "testOptions": {"threads": 4, "mode": "fast"},
              "formats": {
                "known": {"threads": 2},
                "unknown": {"option": "value"}
              },
              "zzzLastSection": [1, 2, 3]
            }""";

    @TempDir
    Path tempDir;

    @Test
    public void unboundSectionsArePreserved() throws Exception {
        Config config = read(INPUT);
        TestOptions options = config.get(TestOptions.class);
        assertEquals(4, options.threads);
        assertEquals("fast", options.mode);

        JsonNode output = writeAndParse(config);
        JsonNode input = JsonMapping.mapper().readTree(INPUT);
        assertEquals(input.get("somePluginOptions"), output.get("somePluginOptions"));
        assertEquals(input.get("formats"), output.get("formats"));
        assertEquals(input.get("zzzLastSection"), output.get("zzzLastSection"));
        assertEquals(List.of("somePluginOptions", "testOptions", "formats", "zzzLastSection"),
                List.copyOf(output.propertyNames()));
    }

    @Test
    public void boundSectionIsCachedAndWritten() throws Exception {
        Config config = read(INPUT);
        TestOptions options = config.get(TestOptions.class);
        assertSame(options, config.get(TestOptions.class));

        options.threads = 8;
        assertEquals(8, writeAndParse(config).get("testOptions").get("threads").intValue());
    }

    @Test
    public void nestedConfigObjectIsBoundLazily() throws Exception {
        FormatsOptions holder = JsonMapping.mapper().readValue(
                "{\"formats\": {\"known\": {\"threads\": 2}, \"unknown\": {\"option\": \"value\"}}}",
                FormatsOptions.class);
        assertEquals(2, holder.formats.get("known", TestOptions.class).threads);
        assertTrue(holder.formats.isPresent("unknown"));
        assertEquals(List.of("known", "unknown"), List.copyOf(holder.formats.getNames()));
    }

    @Test
    public void typedApi() throws Exception {
        ConfigObject<Object> configObject = ConfigObject.empty();
        assertFalse(configObject.isPresent(TestOptions.class));
        assertNull(configObject.get(TestOptions.class));

        TestOptions options = configObject.computeIfAbsent(TestOptions.class, TestOptions::new);
        assertSame(options, configObject.get("testOptions", TestOptions.class));
        assertNotNull(configObject.getOrElse("other", TestOptions.class, TestOptions::new));
        assertFalse(configObject.isPresent("other"));

        assertTrue(configObject.remove(TestOptions.class));
        assertFalse(configObject.isPresent(TestOptions.class));
    }

    @Test
    public void invalidSectionFailsOnAccess() throws Exception {
        Config config = read("{\"testOptions\": {\"threads\": \"many\"}}");
        assertThrows(ConfigException.class, () -> config.get(TestOptions.class));
    }

    @Test
    public void invalidJsonFailsOnRead() throws Exception {
        assertThrows(ConfigException.class, () -> read("{\"testOptions\": "));
    }

    @Test
    public void emptyFileReturnsNull() throws Exception {
        assertNull(read("  "));
    }

    private Config read(String content) throws Exception {
        Path file = tempDir.resolve("input.json");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return ConfigManager.newInstance().read(file, Config.class);
    }

    private JsonNode writeAndParse(Object config) throws Exception {
        Path file = tempDir.resolve("output.json");
        ConfigManager.newInstance().write(config, file);
        return JsonMapping.mapper().readTree(Files.readString(file, StandardCharsets.UTF_8));
    }

    @SerializableConfig(name = "testOptions")
    public static class TestOptions {
        private int threads;
        private String mode;
    }

    public static class FormatsOptions {
        private ConfigObject<Object> formats;
    }
}
