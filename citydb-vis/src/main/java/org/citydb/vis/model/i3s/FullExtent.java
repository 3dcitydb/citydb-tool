/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.model.i3s;

import com.alibaba.fastjson2.annotation.JSONType;

@JSONType(alphabetic = false)
public record FullExtent(double xmin, double ymin, double xmax, double ymax,
                         double zmin, double zmax) {
    public static FullExtent from(double[] extent) {
        if (extent == null) {
            return null;
        }
        return new FullExtent(extent[0], extent[1], extent[3], extent[4], extent[2], extent[5]);
    }
}
