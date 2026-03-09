/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.core.file.output;

import org.citydb.core.file.FileType;
import org.citydb.core.file.OutputFile;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class RegularOutputFile extends OutputFile {

    public RegularOutputFile(Path file) {
        super(file, FileType.REGULAR);
    }

    @Override
    public OutputStream openStream() throws IOException {
        return Files.newOutputStream(getFile());
    }

    @Override
    public String resolve(String... paths) {
        return Paths.get(getFile().getParent().toString(), paths).toString();
    }

    @Override
    public void createDirectories(String path) throws IOException {
        Files.createDirectories(getFile().getParent().resolve(path));
    }

    @Override
    public OutputStream newOutputStream(String file) throws IOException {
        return Files.newOutputStream(Paths.get(file));
    }

    @Override
    public void close() throws IOException {
        // nothing to do
    }
}
