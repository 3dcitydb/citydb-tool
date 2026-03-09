/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.core.file;

import org.apache.tika.mime.MediaType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public abstract class InputFile implements AutoCloseable {
    private final Path file;
    private final FileType fileType;
    private final MediaType mediaType;

    protected InputFile(Path file, FileType fileType, MediaType mediaType) {
        Objects.requireNonNull(file, "The file path must not be null.");
        this.file = file.toAbsolutePath().normalize();
        this.fileType = Objects.requireNonNull(fileType, "The file type must not be null.");
        this.mediaType = mediaType;
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

    public Optional<MediaType> getMediaType() {
        return Optional.ofNullable(mediaType);
    }
}
