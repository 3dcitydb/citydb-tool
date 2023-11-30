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

package org.citydb.io;

import org.citydb.core.CoreConstants;
import org.citydb.core.file.OutputFile;
import org.citydb.core.file.output.GZipOutputFile;
import org.citydb.core.file.output.RegularOutputFile;
import org.citydb.core.file.output.ZipOutputFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
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
        file = CoreConstants.WORKING_DIR.resolve(file).normalize().toAbsolutePath();
        Path parent = file.getParent();
        if (!Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        String[] fileName = getFileName(file);
        switch (fileName[1].toLowerCase(Locale.ROOT)) {
            case "zip":
                return new ZipOutputFile(createFileName(fileName[0], defaultFileExtension),
                        file,
                        tempDirectory != null ? tempDirectory : parent,
                        compressionLevel);
            case "gzip":
            case "gz":
                return new GZipOutputFile(file);
            default:
                return new RegularOutputFile(parent.resolve(createFileName(fileName[0], fileName[1])));
        }
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
