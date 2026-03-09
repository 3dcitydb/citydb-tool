/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.core.file.output;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.citydb.core.file.FileType;
import org.citydb.core.file.OutputFile;
import org.citydb.core.file.helper.ParallelZipCreator;
import org.citydb.core.file.helper.Pipe;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;

public class ZipOutputFile extends OutputFile {
    private final String contentFile;
    private Path tempDir;
    private final ParallelZipCreator zipCreator;
    private final Set<String> entries = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public ZipOutputFile(String contentFile, Path zipFile, Path tempDir, int compressionLevel) {
        super(zipFile, FileType.ARCHIVE);
        this.contentFile = Objects.requireNonNull(contentFile, "The content file must not be null.");
        this.tempDir = Objects.requireNonNull(tempDir, "The temporary directory must not be null.");
        zipCreator = new ParallelZipCreator(tempDir, compressionLevel);
    }

    public ZipOutputFile(String contentFile, Path zipFile, Path tempDir) {
        this(contentFile, zipFile, tempDir, Deflater.DEFAULT_COMPRESSION);
    }

    public String getContentFile() {
        return contentFile;
    }

    @Override
    public OutputStream openStream() throws IOException {
        if (!Files.exists(tempDir)) {
            Files.createDirectories(tempDir);
        } else if (!Files.isDirectory(tempDir)) {
            throw new IOException("The temporary ZIP content path " + tempDir + " is not a directory.");
        }

        return newOutputStream(contentFile);
    }

    @Override
    public String resolve(String... paths) {
        return String.join("/", paths).replace("\\", "/");
    }

    @Override
    public void createDirectories(String path) {
        // not required
    }

    @Override
    public OutputStream newOutputStream(String file) {
        if (entries.add(file)) {
            Pipe pipe = new Pipe();
            ZipArchiveEntry entry = new ZipArchiveEntry(file);
            entry.setMethod(ZipEntry.DEFLATED);
            zipCreator.addArchiveEntry(entry, pipe);
            return pipe.source();
        } else {
            return OutputStream.nullOutputStream();
        }
    }

    @Override
    public void close() throws IOException {
        try (ZipArchiveOutputStream target = new ZipArchiveOutputStream(getFile())) {
            zipCreator.writeTo(target);
        } finally {
            entries.clear();
        }
    }
}
