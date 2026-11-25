/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.plugin.metadata;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.citydb.core.version.Version;
import org.citydb.core.version.encoding.VersionReader;
import org.citydb.core.version.encoding.VersionWriter;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class PluginMetadata {
    private String name;
    @JsonSerialize(using = VersionWriter.class)
    @JsonDeserialize(using = VersionReader.class)
    private Version version;
    private String url;
    @JsonProperty("description")
    private final Map<String, String> descriptions = new HashMap<>();
    private PluginVendor vendor;
    private boolean startEnabled = true;

    public Optional<String> getName() {
        return Optional.ofNullable(name);
    }

    public PluginMetadata setName(String name) {
        this.name = name;
        return this;
    }

    public Optional<Version> getVersion() {
        return Optional.ofNullable(version);
    }

    public PluginMetadata setVersion(Version version) {
        this.version = version;
        return this;
    }

    public Optional<String> getUrl() {
        return Optional.ofNullable(url);
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

    public Optional<PluginVendor> getVendor() {
        return Optional.ofNullable(vendor);
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
