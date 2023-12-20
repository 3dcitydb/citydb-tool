/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
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
import com.alibaba.fastjson2.JSONObject;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class ConfigManager {
    private static final ConfigManager instance = new ConfigManager();

    private Config config;

    private ConfigManager() {
    }

    public static ConfigManager getInstance() {
        return instance;
    }

    public Config getConfig() {
        return config;
    }

    public void load(Path path) throws ConfigException {
        Objects.requireNonNull(path, "The path must not be null.");

        try (InputStream inStream = new BufferedInputStream(Files.newInputStream(path))) {
            config = JSON.parseObject(new String(inStream.readAllBytes()), Config.class);
        } catch (IOException e) {
            throw new ConfigException("Failed to read the config file " + path + ".", e);
        } catch (JSONException e) {
            throw new ConfigException("Failed to parse the config file " + path + ".", e);
        }
    }

    public <T> T getConfig(Class<T> type) throws ConfigException {
        JSONObject options = null;

        if (type.getName().equals("org.citydb.operation.importer.ImportOptions")) {
            options = config.getImportConfig().getOperationOptions();
        } else if (type.getName().equals("org.citydb.io.reader.ReadOptions")) {
            options = config.getImportConfig().getReadOptions();
        } else if (type.getName().equals("org.citydb.io.citygml.reader.CityGMLFormatOptions")) {
            options = config.getImportConfig().getFormatOptions().get("CityGML");
        } else if (type.getName().equals("org.citydb.io.citygml.reader.CityJSONFormatOptions")) {
            options = config.getImportConfig().getFormatOptions().get("CityJSON");
        } else if (type.getName().equals("org.citydb.operation.exporter.ExportOptions")) {
            options = config.getExportConfig().getOperationOptions();
        } else if (type.getName().equals("org.citydb.io.writer.WriteOptions")) {
            options = config.getExportConfig().getWriteOptions();
        } else if (type.getName().equals("org.citydb.io.citygml.writer.CityGMLFormatOptions")) {
            options = config.getExportConfig().getFormatOptions().get("CityGML");
        } else if (type.getName().equals("org.citydb.io.citygml.writer.CityJSONFormatOptions")) {
            options = config.getExportConfig().getFormatOptions().get("CityJSON");
        }

        if (options != null) {
            return JSON.parseObject(options.toString(), type);
        } else {
            throw new ConfigException("Unsupported configuration type '" + type.getName() + "'.");
        }
    }

    public <T> T getPluginConfig(String pluginName, Class<T> type) {
        JSONObject pluginConfig = config.getPluginsConfig().get(pluginName);
        if (pluginConfig != null) {
            return JSON.parseObject(pluginConfig.toString(), type);
        }

        return null;
    }
}
