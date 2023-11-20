/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
 * Virtual City Systems, Germany
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

package org.citydb.core.file.helper;

import org.apache.commons.compress.archivers.zip.StreamCompressor;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.parallel.FileBasedScatterGatherBackingStore;
import org.apache.commons.compress.parallel.InputStreamSupplier;
import org.apache.commons.compress.parallel.ScatterGatherBackingStore;
import org.apache.commons.compress.utils.BoundedInputStream;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.Deflater;

public class ScatterZipOutputStream implements AutoCloseable {
    private final ScatterGatherBackingStore dataStore;
    private final ScatterGatherBackingStore entryStore;
    private final StreamCompressor compressor;

    ScatterZipOutputStream(Path tempDir, int compressionLevel) throws IOException {
        dataStore = createBackingStore(tempDir);
        entryStore = createBackingStore(tempDir);
        compressor = StreamCompressor.create(compressionLevel, dataStore);
    }

    ScatterZipOutputStream(Path tempDir) throws IOException {
        this(tempDir, Deflater.DEFAULT_COMPRESSION);
    }

    void addArchiveEntry(ZipArchiveEntry entry, InputStreamSupplier source) throws IOException {
        try (InputStream payloadStream = source.get()) {
            compressor.deflate(payloadStream, entry.getMethod());

            String line = entry.getMethod() + ":" +
                    compressor.getCrc32() + ":" +
                    compressor.getBytesWrittenForLastEntry() + ":" +
                    compressor.getBytesRead() + ":" +
                    entry.getName() + "\n";

            byte[] data = line.getBytes();
            entryStore.writeOut(data, 0, data.length);
        }
    }

    void writeTo(ZipArchiveOutputStream target) throws IOException {
        dataStore.closeForWriting();
        entryStore.closeForWriting();

        try (InputStream stream = dataStore.getInputStream();
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(entryStore.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] items = line.split(":");

                ZipArchiveEntry entry = new ZipArchiveEntry(items[4]);
                entry.setMethod(Integer.parseInt(items[0]));
                entry.setCrc(Long.parseLong(items[1]));
                entry.setCompressedSize(Long.parseLong(items[2]));
                entry.setSize(Long.parseLong(items[3]));

                try (BoundedInputStream raw = new BoundedInputStream(stream, entry.getCompressedSize())) {
                    target.addRawArchiveEntry(entry, raw);
                }
            }
        } catch (RuntimeException e) {
            throw new IOException("Failed to read temporary zip entry.", e);
        }
    }

    private ScatterGatherBackingStore createBackingStore(Path tempDir) throws IOException {
        return new FileBasedScatterGatherBackingStore(Files.createTempFile(tempDir, "zip", ".tmp").toFile());
    }

    @Override
    public void close() throws IOException {
        try {
            dataStore.close();
            entryStore.close();
        } finally {
            compressor.close();
        }
    }
}
