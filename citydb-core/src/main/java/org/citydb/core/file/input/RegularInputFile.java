/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
