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

package org.citydb.core.file.input;

import org.apache.tika.mime.MediaType;
import org.citydb.core.file.FileType;
import org.citydb.core.file.InputFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

public class RegularInputFile extends InputFile {

    public RegularInputFile(Path file, MediaType mediaType) {
        super(file, FileType.REGULAR, mediaType);
    }

    public RegularInputFile(Path file) {
        this(file, null);
    }

    @Override
    public String getContentFile() {
        return getFile().toString();
    }

    @Override
    public InputStream openStream() throws IOException {
        return new BufferedInputStream(Files.newInputStream(getFile()));
    }

    @Override
    public Path resolve(String path) {
        return getFile().getParent().resolve(path);
    }

    @Override
    public String getSeparator() {
        return FileSystems.getDefault().getSeparator();
    }

    @Override
    public void close() throws IOException {
    }
}
