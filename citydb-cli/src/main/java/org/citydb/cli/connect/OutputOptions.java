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
