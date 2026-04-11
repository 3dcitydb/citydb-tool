/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.model;

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
