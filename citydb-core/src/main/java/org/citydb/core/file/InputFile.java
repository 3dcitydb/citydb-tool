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

package org.citydb.core.file;

import org.apache.tika.mime.MediaType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Objects;

public abstract class InputFile implements AutoCloseable {
    private final Path file;
    private final FileType fileType;
    private final MediaType mediaType;

    protected InputFile(Path file, FileType fileType, MediaType mediaType) {
        Objects.requireNonNull(file, "The file path must not be null.");
        this.file = file.toAbsolutePath().normalize();
        this.fileType = Objects.requireNonNull(fileType, "The file type must not be null.");
        this.mediaType = Objects.requireNonNull(mediaType, "The media type must not be null.");
    }

    public abstract String getContentFile();

    public abstract InputStream openStream() throws IOException;

    public abstract Path resolve(String path);

    public abstract String getSeparator();

    @Override
    public abstract void close() throws IOException;

    public Path getFile() {
        return file;
    }

    public FileType getFileType() {
        return fileType;
    }

    public MediaType getMediaType() {
        return mediaType;
    }
}
