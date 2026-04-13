/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.model.i3s;

import com.alibaba.fastjson2.annotation.JSONType;

@JSONType(alphabetic = false)
public record SpatialReference(int wkid, int latestWkid) {
    public static SpatialReference of(int wkid) {
        return new SpatialReference(wkid, wkid);
    }
}
