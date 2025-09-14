/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
 * virtualcitysystems GmbH, Germany
 * https://vc.systems/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
