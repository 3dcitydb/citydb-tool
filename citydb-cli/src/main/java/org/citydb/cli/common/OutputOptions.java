/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.common;

import org.citydb.cli.CliConstants;
import org.citydb.cli.util.Streams;
import picocli.CommandLine;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public abstract class OutputOptions implements Option {
    protected OutputTarget outputTarget = OutputTarget.UNSPECIFIED;

    public enum OutputTarget {
        FILE,
        STDOUT,
        UNSPECIFIED
    }

    public abstract Path getFile();

    protected abstract void setFile(Path file);

    public OutputTarget getOutputTarget() {
        return outputTarget;
    }

    public boolean isOutputSpecified() {
        return outputTarget != OutputTarget.UNSPECIFIED;
    }

    public boolean isWriteToFile() {
        return outputTarget == OutputTarget.FILE;
    }

    public boolean isWriteToStdout() {
        return outputTarget == OutputTarget.STDOUT;
    }

    public OutputStream openStream() throws IOException {
        return switch (outputTarget) {
            case FILE -> {
                Files.createDirectories(getFile().getParent());
                yield Files.newOutputStream(getFile());
            }
            case STDOUT -> Streams.nonClosing(System.out);
            case UNSPECIFIED -> throw new IOException("No output option specified.");
        };
    }

    @Override
    public void preprocess(CommandLine commandLine) throws Exception {
        Path file = getFile();
        if (file != null) {
            if ("-".equals(file.toString())) {
                setFile(null);
                outputTarget = OutputTarget.STDOUT;
            } else {
                setFile(CliConstants.WORKING_DIR.resolve(file));
                outputTarget = OutputTarget.FILE;
            }
        }
    }

    @Override
    public String toString() {
        return switch (outputTarget) {
            case FILE -> getFile().toString();
            case STDOUT -> "standard output";
            case UNSPECIFIED -> "unspecified";
        };
    }
}
