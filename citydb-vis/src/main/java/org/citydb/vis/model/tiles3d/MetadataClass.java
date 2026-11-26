/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.model.tiles3d;

import java.util.Map;

public class MetadataClass {
    private final Map<String, MetadataProperty> properties;

    MetadataClass(Map<String, MetadataProperty> properties) {
        this.properties = properties;
    }
}
