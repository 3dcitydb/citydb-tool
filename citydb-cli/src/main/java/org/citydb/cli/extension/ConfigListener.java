/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.extension;

import org.citydb.cli.ExecutionException;
import org.citydb.config.Config;
import org.citydb.plugin.Extension;

@FunctionalInterface
public interface ConfigListener extends Extension {
    void onLoad(Config config) throws ExecutionException;
}
