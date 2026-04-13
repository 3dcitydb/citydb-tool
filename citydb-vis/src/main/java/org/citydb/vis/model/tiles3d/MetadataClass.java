/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.model.tiles3d;

import com.alibaba.fastjson2.annotation.JSONType;

import java.util.Map;

@JSONType(alphabetic = false)
public class MetadataClass {
    Map<String, MetadataProperty> properties;
}
