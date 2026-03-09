/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.core.file;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Objects;

public abstract class OutputFile implements AutoCloseable {
    private final Path file;
    private final FileType fileType;

    protected OutputFile(Path file, FileType fileType) {
        Objects.requireNonNull(file, "The file path must not be null.");
        this.file = file.toAbsolutePath().normalize();
        this.fileType = Objects.requireNonNull(fileType, "The file type must not be null.");
    }

    public abstract OutputStream openStream() throws IOException;

    public abstract String resolve(String... paths);

    public abstract void createDirectories(String path) throws IOException;

    public abstract OutputStream newOutputStream(String file) throws IOException;

    @Override
    public abstract void close() throws IOException;

    public Path getFile() {
        return file;
    }

    public FileType getFileType() {
        return fileType;
    }
}
