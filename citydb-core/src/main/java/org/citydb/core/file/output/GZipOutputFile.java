/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.core.file.output;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

public class GZipOutputFile extends RegularOutputFile {

    public GZipOutputFile(Path file) {
        super(file);
    }

    @Override
    public OutputStream openStream() throws IOException {
        return new GZIPOutputStream(Files.newOutputStream(getFile()));
    }
}
