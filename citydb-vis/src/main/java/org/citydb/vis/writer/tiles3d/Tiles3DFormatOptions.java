/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.writer.tiles3d;

import org.citydb.vis.writer.VisFormatOptions;

import org.citydb.config.SerializableConfig;

/**
 * 3D Tiles 1.1 format options. Currently all parameters are inherited from
 * {@link VisFormatOptions}; this subclass exists for configuration
 * deserialization ({@code @SerializableConfig}) and as a hook for future
 * 3D Tiles-only options.
 */
@SerializableConfig(name = "3DTiles")
public class Tiles3DFormatOptions extends VisFormatOptions {
}
