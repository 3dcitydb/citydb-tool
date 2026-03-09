/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io;

import org.citydb.core.file.OutputFile;
import org.citydb.core.file.output.GZipOutputFile;
import org.citydb.core.file.output.RegularOutputFile;
import org.citydb.core.file.output.ZipOutputFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.zip.Deflater;

public class OutputFileBuilder {
    private String defaultFileExtension = "";
    private Path tempDirectory;
    private int compressionLevel = Deflater.DEFAULT_COMPRESSION;

    private OutputFileBuilder() {
    }

    public static OutputFileBuilder newInstance() {
        return new OutputFileBuilder();
    }

    public OutputFileBuilder defaultFileExtension(String extension) {
        if (extension != null) {
            defaultFileExtension = extension.startsWith(".") ?
                    extension.substring(1) :
                    extension;
        }

        return this;
    }

    public OutputFileBuilder tempDirectory(Path tempDirectory) {
        this.tempDirectory = tempDirectory;
        return this;
    }

    public OutputFileBuilder compressionLevel(int compressionLevel) {
        if (compressionLevel >= Deflater.NO_COMPRESSION
                && compressionLevel <= Deflater.BEST_COMPRESSION) {
            this.compressionLevel = compressionLevel;
        }

        return this;
    }

    public OutputFile newOutputFile(Path file) throws IOException {
        file = Objects.requireNonNull(file, "The output file must not be null.")
                .toAbsolutePath()
                .normalize();

        Path parent = file.getParent();
        if (!Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        String[] fileName = getFileName(file);
        return switch (fileName[1].toLowerCase(Locale.ROOT)) {
            case "zip" -> new ZipOutputFile(createFileName(fileName[0], defaultFileExtension),
                    file,
                    tempDirectory != null ? tempDirectory : parent,
                    compressionLevel);
            case "gzip", "gz" -> new GZipOutputFile(file);
            default -> new RegularOutputFile(parent.resolve(createFileName(fileName[0], fileName[1])));
        };
    }

    private String[] getFileName(Path file) {
        String fileName = file.getFileName().toString();
        int index = fileName.lastIndexOf('.');
        return index > 0 ?
                new String[]{fileName.substring(0, index), fileName.substring(index + 1)} :
                new String[]{fileName, defaultFileExtension};
    }

    private String createFileName(String fileName, String extension) {
        return fileName + (!extension.isEmpty() ? "." + extension : "");
    }
}
