/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.model.i3s;

public record SpatialReference(int wkid, int latestWkid) {
    public static SpatialReference of(int wkid) {
        return new SpatialReference(wkid, wkid);
    }
}
