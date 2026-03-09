/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.exporter.options;

import org.citydb.cli.common.Option;
import picocli.CommandLine;

import java.nio.file.Path;

public class OutputFileOptions implements Option {
    @CommandLine.Option(names = {"-o", "--output"}, required = true,
            description = "Name of the output file.")
    private Path file;

    @CommandLine.Option(names = "--output-encoding",
            description = "Encoding to use for the output file.")
    private String encoding;

    public Path getFile() {
        return file;
    }

    public String getEncoding() {
        return encoding;
    }
}
