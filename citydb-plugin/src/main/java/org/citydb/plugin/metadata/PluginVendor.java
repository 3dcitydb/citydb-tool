/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.plugin.metadata;

public class PluginVendor {
    private String name;
    private String url;

    public String getName() {
        return name;
    }

    public PluginVendor setName(String name) {
        this.name = name;
        return this;
    }

    public String getUrl() {
        return url;
    }

    public PluginVendor setUrl(String url) {
        this.url = url;
        return this;
    }
}
