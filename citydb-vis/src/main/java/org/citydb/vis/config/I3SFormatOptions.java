/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.config;

import org.citydb.config.SerializableConfig;

/**
 * I3S-specific format options.
 */
@SerializableConfig(name = "I3S")
public class I3SFormatOptions extends VisFormatOptions {
    private boolean slpk;
    private boolean obb;

    public boolean isSlpk() {
        return slpk;
    }

    public I3SFormatOptions setSlpk(boolean slpk) {
        this.slpk = slpk;
        return this;
    }

    public boolean isObb() {
        return obb;
    }

    public I3SFormatOptions setObb(boolean obb) {
        this.obb = obb;
        return this;
    }
}
