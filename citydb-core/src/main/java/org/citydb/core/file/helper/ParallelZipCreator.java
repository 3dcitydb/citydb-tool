/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
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

package org.citydb.core.file.helper;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.parallel.InputStreamSupplier;
import org.citydb.core.concurrent.CountLatch;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.Deflater;

public class ParallelZipCreator {
    private final ExecutorService service;
    private final ThreadLocal<ScatterZipOutputStream> threadLocal;
    private final Deque<ScatterZipOutputStream> streams = new ConcurrentLinkedDeque<>();
    private final CountLatch countLatch = new CountLatch();

    private IOException exception;

    public ParallelZipCreator(Path tempDir, int compressionLevel) {
        Objects.requireNonNull(tempDir, "The temporary directory must not be null.");
        if ((compressionLevel < Deflater.NO_COMPRESSION
                || compressionLevel > Deflater.BEST_COMPRESSION)
                && compressionLevel != Deflater.DEFAULT_COMPRESSION) {
            throw new IllegalArgumentException("Invalid compression level.");
        }

        service = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()));
        threadLocal = ThreadLocal.withInitial(() -> {
            try {
                ScatterZipOutputStream stream = new ScatterZipOutputStream(tempDir, compressionLevel);
                streams.push(stream);
                return stream;
            } catch (IOException e) {
                throw new RuntimeException("Failed to create scatter ZIP output stream.", e);
            }
        });
    }

    public ParallelZipCreator(Path tempDir) {
        this(tempDir, Deflater.DEFAULT_COMPRESSION);
    }

    public void addArchiveEntry(ZipArchiveEntry entry, InputStreamSupplier source) {
        countLatch.increment();
        service.execute(() -> {
            try {
                threadLocal.get().addArchiveEntry(entry, source);
            } catch (IOException e) {
                exception = e;
            } finally {
                countLatch.decrement();
            }
        });
    }

    public void writeTo(ZipArchiveOutputStream target) throws IOException {
        try {
            countLatch.await();
            if (exception != null) {
                throw exception;
            }

            for (ScatterZipOutputStream stream : streams) {
                stream.writeTo(target);
                stream.close();
            }
        } finally {
            service.shutdown();
            close();
        }
    }

    private void close() {
        for (ScatterZipOutputStream stream : streams) {
            try {
                stream.close();
            } catch (IOException e) {
                //
            }
        }
    }
}
