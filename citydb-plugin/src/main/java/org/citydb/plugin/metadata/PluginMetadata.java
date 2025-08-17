/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
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

package org.citydb.plugin.metadata;

import com.alibaba.fastjson2.annotation.JSONField;

import java.util.HashMap;
import java.util.Map;

public class PluginMetadata {
    private String name;
    private String version;
    private String url;
    @JSONField(name = "description")
    private final Map<String, String> descriptions = new HashMap<>();
    private PluginVendor vendor;
    private boolean startEnabled = true;

    public String getName() {
        return name;
    }

    public PluginMetadata setName(String name) {
        this.name = name;
        return this;
    }

    public String getVersion() {
        return version;
    }

    public PluginMetadata setVersion(String version) {
        this.version = version;
        return this;
    }

    public String getUrl() {
        return url;
    }

    public PluginMetadata setUrl(String url) {
        this.url = url;
        return this;
    }

    public Map<String, String> getDescriptions() {
        return descriptions;
    }

    public PluginMetadata addDescription(String language, String description) {
        descriptions.put(language, description);
        return this;
    }

    public PluginVendor getVendor() {
        return vendor;
    }

    public PluginMetadata setVendor(PluginVendor vendor) {
        this.vendor = vendor;
        return this;
    }

    public boolean isStartEnabled() {
        return startEnabled;
    }

    public PluginMetadata setStartEnabled(boolean startEnabled) {
        this.startEnabled = startEnabled;
        return this;
    }
}
