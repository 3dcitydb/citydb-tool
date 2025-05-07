/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
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
