/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.config;

import org.citydb.cli.deleter.DeleteOptions;
import org.citydb.cli.exporter.ExportOptions;
import org.citydb.cli.importer.ImportOptions;
import org.citydb.config.Config;
import org.citydb.config.ConfigManager;
import org.citydb.config.common.ConfigObject;
import org.citydb.database.DatabaseOptions;
import org.citydb.io.reader.ReadOptions;
import org.citydb.io.writer.WriteOptions;
import org.citydb.util.report.ReportOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

public class ConfigGoldenTest {
    private static final Path GOLDEN = Path.of("src/test/resources/golden/config");
    private static final boolean UPDATE = System.getenv("GOLDEN_UPDATE") != null;
    // fastjson2 writes integer map keys unquoted, so the golden files need this to be readable
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_UNQUOTED_PROPERTY_NAMES)
            .build();

    @TempDir
    Path tempDir;

    @Test
    public void defaults() throws Exception {
        Config config = new Config();
        config.set(new ImportOptions());
        config.set(new ExportOptions());
        config.set(new DeleteOptions());
        config.set(new DatabaseOptions());
        config.set(new ReportOptions());
        config.set(withFormatOptions(new ReadOptions()));
        config.set(withFormatOptions(new WriteOptions()));

        check("defaults.json", write(config));
    }

    @Test
    public void fullRoundTrip() throws Exception {
        Config config = read("full-input.json");
        bindAll(config);
        check("full-output.json", write(config));
    }

    private void bindAll(Config config) throws Exception {
        assertNotNull(config.get(ImportOptions.class));
        assertNotNull(config.get(ExportOptions.class));
        assertNotNull(config.get(DeleteOptions.class));
        assertNotNull(config.get(DatabaseOptions.class));
        assertNotNull(config.get(ReportOptions.class));

        ReadOptions readOptions = config.get(ReadOptions.class);
        assertNotNull(readOptions);
        assertNotNull(readOptions.getFormatOptions()
                .get(org.citydb.io.citygml.reader.CityGMLFormatOptions.class));
        assertNotNull(readOptions.getFormatOptions()
                .get(org.citydb.io.citygml.reader.CityJSONFormatOptions.class));

        WriteOptions writeOptions = config.get(WriteOptions.class);
        assertNotNull(writeOptions);
        assertNotNull(writeOptions.getFormatOptions()
                .get(org.citydb.io.citygml.writer.CityGMLFormatOptions.class));
        assertNotNull(writeOptions.getFormatOptions()
                .get(org.citydb.io.citygml.writer.CityJSONFormatOptions.class));
    }

    private ReadOptions withFormatOptions(ReadOptions options) {
        options.setFormatOptions(ConfigObject.of(List.of(
                new org.citydb.io.citygml.reader.CityGMLFormatOptions(),
                new org.citydb.io.citygml.reader.CityJSONFormatOptions())));
        return options;
    }

    private WriteOptions withFormatOptions(WriteOptions options) {
        options.setFormatOptions(ConfigObject.of(List.of(
                new org.citydb.io.citygml.writer.CityGMLFormatOptions(),
                new org.citydb.io.citygml.writer.CityJSONFormatOptions())));
        return options;
    }

    private Config read(String name) throws Exception {
        return ConfigManager.newInstance().read(GOLDEN.resolve(name), Config.class);
    }

    private String write(Object config) throws Exception {
        Path file = tempDir.resolve("config.json");
        ConfigManager.newInstance().write(config, file);
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    private void check(String name, String actual) throws Exception {
        Path expectedFile = GOLDEN.resolve(name);
        if (UPDATE) {
            Files.createDirectories(GOLDEN);
            Files.writeString(expectedFile, actual, StandardCharsets.UTF_8);
            return;
        }

        String expected = Files.readString(expectedFile, StandardCharsets.UTF_8);
        JsonNode expectedTree = MAPPER.readTree(expected);
        JsonNode actualTree = MAPPER.readTree(actual);
        if (!expectedTree.equals(actualTree)) {
            fail(name + " differs from the golden file:\n" + differences(expectedTree, actualTree, "$"));
        }
    }

    private String differences(JsonNode expected, JsonNode actual, String path) {
        StringBuilder result = new StringBuilder();
        collectDifferences(expected, actual, path, result);
        return result.toString();
    }

    private void collectDifferences(JsonNode expected, JsonNode actual, String path, StringBuilder result) {
        if (expected == null || actual == null) {
            result.append(path).append(": expected ").append(expected).append(", actual ").append(actual).append('\n');
        } else if (expected.isObject() && actual.isObject()) {
            List<String> names = new ArrayList<>(expected.propertyNames());
            actual.propertyNames().stream().filter(n -> !names.contains(n)).forEach(names::add);
            for (String n : names) {
                collectDifferences(expected.get(n), actual.get(n), path + "." + n, result);
            }
        } else if (expected.isArray() && actual.isArray() && expected.size() == actual.size()) {
            for (int i = 0; i < expected.size(); i++) {
                collectDifferences(expected.get(i), actual.get(i), path + "[" + i + "]", result);
            }
        } else if (!expected.equals(actual)) {
            result.append(path).append(": expected ").append(expected).append(", actual ").append(actual).append('\n');
        }
    }
}
