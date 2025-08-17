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
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Objects;

public class ZipInputFile extends InputFile {
    private final String entryName;
    private final URI fileURI;
    private FileSystem fileSystem;

    public ZipInputFile(String entryName, Path zipFile, URI fileURI, MediaType mediaType) {
        super(zipFile, FileType.ARCHIVE, mediaType);
        this.entryName = Objects.requireNonNull(entryName, "The entry name must not be null.");
        this.fileURI = Objects.requireNonNull(fileURI, "The file URI must not be null.");
    }

    @Override
    public String getContentFile() {
        return Path.of(getFile().toString(), entryName).toString();
    }

    @Override
    public InputStream openStream() throws IOException {
        return new BufferedInputStream(Files.newInputStream(getFileSystem().getPath(entryName)));
    }

    @Override
    public Path resolve(String path) {
        return getFileSystem().getPath(entryName).getParent().resolve(path);
    }

    @Override
    public String getSeparator() {
        return getFileSystem().getSeparator();
    }

    public String getEntryName() {
        return entryName;
    }

    public URI getFileURI() {
        return fileURI;
    }

    private FileSystem getFileSystem() {
        if (fileSystem == null) {
            try {
                fileSystem = FileSystems.getFileSystem(fileURI);
            } catch (Throwable e) {
                //
            }
        }

        if (fileSystem == null) {
            try {
                fileSystem = FileSystems.newFileSystem(fileURI, Collections.emptyMap());
            } catch (IOException e) {
                throw new RuntimeException("Failed to open ZIP file " + getFile() + ".");
            }
        }

        return fileSystem;
    }

    @Override
    public void close() throws IOException {
        if (fileSystem != null) {
            fileSystem.close();
        }
    }
}
