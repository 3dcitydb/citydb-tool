/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.core.file.input;

import org.apache.tika.mime.MediaType;
import org.citydb.core.file.FileType;
import org.citydb.core.file.InputFile;
import org.citydb.core.file.helper.SharedZipFileSystem;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class ZipInputFile extends InputFile {
    private final String entryName;
    private final URI fileURI;
    private final SharedZipFileSystem fileSystem;
    private boolean closed;

    public ZipInputFile(String entryName, Path zipFile, URI fileURI, MediaType mediaType) {
        super(zipFile, FileType.ARCHIVE, mediaType);
        this.entryName = Objects.requireNonNull(entryName, "The entry name must not be null.");
        this.fileURI = Objects.requireNonNull(fileURI, "The file URI must not be null.");
        fileSystem = SharedZipFileSystem.acquire(fileURI);
    }

    public ZipInputFile(String entryName, Path zipFile, URI fileURI) {
        this(entryName, zipFile, fileURI, null);
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
        try {
            return fileSystem.getFileSystem();
        } catch (IOException e) {
            throw new RuntimeException("Failed to open ZIP file " + getFile() + ".", e);
        }
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            fileSystem.release();
        }
    }
}
