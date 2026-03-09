/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.core.file.helper;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.citydb.core.concurrent.CountLatch;

import java.io.IOException;
import java.io.UncheckedIOException;
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
                throw new UncheckedIOException("Failed to create scatter ZIP output stream.", e);
            }
        });
    }

    public ParallelZipCreator(Path tempDir) {
        this(tempDir, Deflater.DEFAULT_COMPRESSION);
    }

    public void addArchiveEntry(ZipArchiveEntry entry, Pipe pipe) {
        countLatch.increment();
        service.execute(() -> {
            try {
                threadLocal.get().addArchiveEntry(entry, pipe::sink);
            } catch (Exception e) {
                pipe.cancel(e.getMessage(), e.getCause());
            } finally {
                countLatch.decrement();
            }
        });
    }

    public void writeTo(ZipArchiveOutputStream target) throws IOException {
        try {
            countLatch.await();
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
