/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
 * virtualcitysystems GmbH, Germany
 * https://vc.systems/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
