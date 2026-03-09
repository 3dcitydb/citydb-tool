/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.common;

import picocli.CommandLine;

public interface Option {
    default void preprocess(CommandLine commandLine) throws Exception {
    }
}
