/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.connect;

import picocli.CommandLine;

import java.nio.file.Path;

public class OutputOptions extends org.citydb.cli.common.OutputOptions {
    @CommandLine.Option(names = {"-o", "--output"}, paramLabel = "<file|->",
            description = "Write connection info to JSON. Use '-' for stdout.")
    private Path file;

    @Override
    public Path getFile() {
        return file;
    }

    @Override
    protected void setFile(Path file) {
        this.file = file;
    }
}
