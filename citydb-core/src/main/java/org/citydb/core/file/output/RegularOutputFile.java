/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
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

package org.citydb.core.file.output;

import org.citydb.core.file.FileType;
import org.citydb.core.file.OutputFile;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class RegularOutputFile extends OutputFile {

    public RegularOutputFile(Path file) {
        super(file, FileType.REGULAR);
    }

    @Override
    public OutputStream openStream() throws IOException {
        return Files.newOutputStream(getFile());
    }

    @Override
    public String resolve(String... paths) {
        return Paths.get(getFile().getParent().toString(), paths).toString();
    }

    @Override
    public void createDirectories(String path) throws IOException {
        Files.createDirectories(getFile().getParent().resolve(path));
    }

    @Override
    public OutputStream newOutputStream(String file) throws IOException {
        return Files.newOutputStream(Paths.get(file));
    }

    @Override
    public void close() throws IOException {
        // nothing to do
    }
}
