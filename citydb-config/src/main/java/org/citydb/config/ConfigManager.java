/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.config;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

public class ConfigManager {

    private ConfigManager() {
    }

    public static ConfigManager newInstance() {
        return new ConfigManager();
    }

    public <T> T read(Path inputFile, Class<T> type) throws ConfigException, IOException {
        Objects.requireNonNull(inputFile, "The input file must not be null.");
        try (InputStream stream = new BufferedInputStream(Files.newInputStream(inputFile))) {
            return JSON.parseObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8), type,
                    JSONReader.Feature.UseDoubleForDecimals);
        } catch (JSONException e) {
            throw new ConfigException("Failed to parse config file " + inputFile + ".", e);
        }
    }

    public <T> T read(Path inputFile, Class<T> type, Supplier<T> supplier) throws ConfigException, IOException {
        T config = read(inputFile, type);
        return config != null ? config : supplier.get();
    }

    public void write(Object config, Path outputFile) throws IOException {
        Objects.requireNonNull(config, "The config object must not be null.");
        outputFile = Objects.requireNonNull(outputFile, "The output file must not be null.")
                .toAbsolutePath()
                .normalize();

        Files.writeString(outputFile, JSON.toJSONString(config, JSONWriter.Feature.FieldBased,
                JSONWriter.Feature.PrettyFormatWith2Space));
    }
}
