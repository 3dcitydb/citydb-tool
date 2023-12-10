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
