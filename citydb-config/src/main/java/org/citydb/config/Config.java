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
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.annotation.JSONField;
import org.citydb.config.configs.ExportConfig;
import org.citydb.config.configs.ImportConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Config {
    @JSONField(name = "import")
    private ImportConfig importConfig;
    @JSONField(name = "export")
    private ExportConfig exportConfig;
    @JSONField(name = "plugins")
    private Map<String, JSONObject> pluginsConfig;

    public ImportConfig getImportConfig() {
        if (importConfig == null) {
            importConfig = new ImportConfig();
        }

        return importConfig;
    }

    public Config setImportConfig(ImportConfig importConfig) {
        this.importConfig = importConfig;
        return this;
    }

    public ExportConfig getExportConfig() {
        if (exportConfig == null) {
            exportConfig = new ExportConfig();
        }

        return exportConfig;
    }

    public Config setExportConfig(ExportConfig exportConfig) {
        this.exportConfig = exportConfig;
        return this;
    }

    public Map<String, JSONObject> getPluginsConfig() {
        if (pluginsConfig == null) {
            pluginsConfig = new HashMap<>();
        }

        return pluginsConfig;
    }

    public Config setPluginsConfig(Map<String, JSONObject> pluginsConfig) {
        this.pluginsConfig = pluginsConfig;
        return this;
    }

    public void writeTo(Path target) throws ConfigException {
        Objects.requireNonNull(target, "The target path must not be null.");
        try {
            Files.write(target, JSON.toJSONString(this, JSONWriter.Feature.PrettyFormat).getBytes());
        } catch (IOException e) {
            throw new ConfigException("Failed to write config file to " + target, e);
        }
    }
}
