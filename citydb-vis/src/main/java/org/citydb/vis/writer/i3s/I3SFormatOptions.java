/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.writer.i3s;

import org.citydb.vis.writer.VisFormatOptions;

import org.citydb.config.SerializableConfig;

/**
 * I3S-specific format options. Currently all parameters are inherited from
 * {@link VisFormatOptions}; this subclass exists for configuration
 * deserialization ({@code @SerializableConfig}) and as a hook for future
 * I3S-only options.
 */
@SerializableConfig(name = "I3S")
public class I3SFormatOptions extends VisFormatOptions {
}
