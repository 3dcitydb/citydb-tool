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

public class JsonOutputOptions implements Option {
    @CommandLine.Option(names = {"-o", "--output"}, paramLabel = "<file|->",
            description = "Write output as a JSON file. Use '-' for stdout.")
    private Path file;

    private boolean writeToStdout;

    public Path getFile() {
        return file;
    }

    public boolean isWriteToStdout() {
        return writeToStdout;
    }

    public boolean isOutputSpecified() {
        return file != null || writeToStdout;
    }

    public OutputStream openStream() throws IOException {
        if (isOutputSpecified()) {
            return writeToStdout ?
                    Streams.nonClosing(System.out) :
                    Files.newOutputStream(file);
        } else {
            throw new IOException("No output option specified.");
        }
    }

    @Override
    public void preprocess(CommandLine commandLine) throws Exception {
        if (file != null) {
            if ("-".equals(file.toString())) {
                writeToStdout = true;
                file = null;
            } else {
                file = CliConstants.WORKING_DIR.resolve(file);
            }
        }
    }
}
