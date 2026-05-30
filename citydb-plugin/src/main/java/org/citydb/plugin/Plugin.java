/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.plugin;

import org.citydb.plugin.metadata.PluginMetadata;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public abstract class Plugin {
    private PluginMetadata metadata;
    private Path basePath;
    private boolean enabled;

    public abstract List<Extension> getExtensions();

    public void initialize() throws PluginException {
    }

    void initialize(PluginMetadata metadata, Path basePath) throws PluginException {
        this.metadata = Objects.requireNonNull(metadata, "The plugin metadata must not be null.");
        this.basePath = basePath;
        enabled = metadata.isStartEnabled();

        initialize();
    }

    public PluginMetadata getMetadata() {
        return metadata;
    }

    Plugin setMetadata(PluginMetadata metadata) {
        this.metadata = metadata;
        return this;
    }

    public Path getBasePath() {
        return basePath;
    }

    protected Plugin setBasePath(Path basePath) {
        this.basePath = basePath;
        return this;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Plugin setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }
}
