/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.core.file.input;

import org.apache.tika.mime.MediaType;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

public class GZipInputFile extends RegularInputFile {

    public GZipInputFile(Path file, MediaType mediaType) {
        super(file, mediaType);
    }

    public GZipInputFile(Path file) {
        this(file, null);
    }

    @Override
    public InputStream openStream() throws IOException {
        return new GZIPInputStream(new BufferedInputStream(Files.newInputStream(getFile())));
    }
}
