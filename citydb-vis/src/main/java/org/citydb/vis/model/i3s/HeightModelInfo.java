/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.model.i3s;

import com.alibaba.fastjson2.annotation.JSONType;

@JSONType(alphabetic = false)
public record HeightModelInfo(String heightModel, String vertCRS, String heightUnit) {
    public static HeightModelInfo egm96Meter() {
        return new HeightModelInfo("gravity_related_height", "EGM96_Geoid", "meter");
    }
}
