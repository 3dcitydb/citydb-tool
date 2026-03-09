/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.common;

import picocli.CommandLine;

public class IndexOptions implements Option {
    public enum Mode {keep, drop, drop_create}

    @CommandLine.Option(names = "--index-mode", defaultValue = "keep",
            description = "Index mode: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}). Consider dropping " +
                    "indexes when processing large quantities of data.")
    protected Mode mode;

    public Mode getMode() {
        return mode;
    }
}
