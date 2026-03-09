/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.plugin;

import org.citydb.plugin.metadata.PluginMetadata;

import java.util.List;

public abstract class Plugin {
    private PluginMetadata metadata;
    private boolean enabled;

    public abstract List<Class<? extends Extension>> getExtensions();

    Plugin initialize(PluginMetadata metadata) {
        if (metadata == null) {
            metadata = new PluginMetadata();
        }

        if (metadata.getName() == null) {
            metadata.setName(getClass().getName());
        }

        this.metadata = metadata;
        enabled = metadata.isStartEnabled();
        return this;
    }

    public PluginMetadata getMetadata() {
        return metadata;
    }

    Plugin setMetadata(PluginMetadata metadata) {
        this.metadata = metadata;
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
