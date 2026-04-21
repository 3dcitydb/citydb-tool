/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.model.i3s;

import com.alibaba.fastjson2.annotation.JSONType;

import java.util.List;

public class TextureSetDefinition {
    private List<Format> formats;

    public static TextureSetDefinition jpeg() {
        TextureSetDefinition definition = new TextureSetDefinition();
        definition.formats = List.of(new Format("0", "jpg"));
        return definition;
    }

    @JSONType(alphabetic = false)
    public record Format(String name, String format) {
    }
}
