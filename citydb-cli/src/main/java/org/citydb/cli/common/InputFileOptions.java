/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.common;

import picocli.CommandLine;

public class InputFileOptions implements Option {
    @CommandLine.Parameters(paramLabel = "<file>", arity = "1",
            description = "One or more files and directories to process (glob patterns allowed).")
    private String[] files;

    @CommandLine.Option(names = "--input-encoding",
            description = "Encoding of input file(s).")
    private String encoding;

    public String[] getFiles() {
        return files;
    }

    public String joinFiles() {
        return String.join(", ", files);
    }

    public String getEncoding() {
        return encoding;
    }
}
