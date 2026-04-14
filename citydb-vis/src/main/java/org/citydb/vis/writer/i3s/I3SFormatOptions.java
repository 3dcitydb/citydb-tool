/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.writer.i3s;

import org.citydb.vis.writer.VisFormatOptions;

import org.citydb.config.SerializableConfig;

/**
 * I3S-specific format options.
 */
@SerializableConfig(name = "I3S")
public class I3SFormatOptions extends VisFormatOptions {
    private boolean slpk;

    public boolean isSlpk() {
        return slpk;
    }

    public I3SFormatOptions setSlpk(boolean slpk) {
        this.slpk = slpk;
        return this;
    }
}
