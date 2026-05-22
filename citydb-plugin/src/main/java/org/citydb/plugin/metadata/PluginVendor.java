/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.plugin.metadata;

import java.util.Optional;

public class PluginVendor {
    private String name;
    private String url;

    public Optional<String> getName() {
        return Optional.ofNullable(name);
    }

    public PluginVendor setName(String name) {
        this.name = name;
        return this;
    }

    public Optional<String> getUrl() {
        return Optional.ofNullable(url);
    }

    public PluginVendor setUrl(String url) {
        this.url = url;
        return this;
    }
}
