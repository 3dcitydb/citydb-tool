/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;

/**
 * Shared file path utilities for visualization format writers.
 */
public class FileHelper {
    private FileHelper() {
    }

    /**
     * Read from a {@link FileChannel} at the given position until the
     * buffer is completely filled.
     */
    public static void readFully(FileChannel channel, ByteBuffer buf,
                                 long startPosition) throws IOException {
        int totalRead = 0;
        int needed = buf.remaining();
        while (totalRead < needed) {
            int read = channel.read(buf, startPosition + totalRead);
            if (read < 0) {
                throw new IOException("Unexpected end of file at position "
                        + (startPosition + totalRead));
            }
            totalRead += read;
        }
    }

    /**
     * Strip the file extension from a path, returning the path without
     * the trailing {@code .ext} suffix. Used to derive the output
     * directory name from the output file path (e.g., {@code data.i3s}
     * → {@code data/}).
     */
    public static Path stripExtension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            return path.resolveSibling(name.substring(0, dot));
        }
        return path;
    }

    /**
     * Recursively delete a directory tree. Files are removed in parallel;
     * directories afterwards in reverse (depth-first) order.
     * Silently ignores individual delete failures (best-effort cleanup).
     * <p>
     * {@code parallelism} bounds the file-deletion fan-out. It is sourced from
     * the resolved {@code --threads} count so cleanup honors the same thread
     * budget as the rest of the export rather than saturating the JVM common
     * pool. Values {@code <= 1} delete serially (no pool is created).
     */
    public static void deleteDirectoryTree(Path root, int parallelism) {
        if (!Files.isDirectory(root)) {
            return;
        }

        List<Path> files = new ArrayList<>();
        List<Path> dirs = new ArrayList<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    files.add(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    dirs.add(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            return;
        }

        deleteFiles(files, parallelism);

        for (Path dir : dirs) {
            try {
                Files.delete(dir);
            } catch (IOException ignored) {
                //
            }
        }
    }

    /**
     * Best-effort delete of {@code files}, fanned out across at most
     * {@code parallelism} threads. Runs the parallel stream inside a dedicated
     * {@link ForkJoinPool} so the fan-out is bounded by {@code --threads}
     * instead of the JVM-wide common pool (sized to all cores). Falls back to
     * a serial loop when {@code parallelism <= 1}.
     */
    private static void deleteFiles(List<Path> files, int parallelism) {
        if (parallelism <= 1 || files.size() <= 1) {
            for (Path file : files) {
                deleteQuietly(file);
            }
            return;
        }

        ForkJoinPool pool = new ForkJoinPool(parallelism);
        try {
            pool.submit(() -> files.parallelStream().forEach(FileHelper::deleteQuietly)).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // Best-effort cleanup: finish serially on the calling thread.
            files.forEach(FileHelper::deleteQuietly);
        } catch (ExecutionException ignored) {
            //
        } finally {
            pool.shutdown();
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.delete(file);
        } catch (IOException ignored) {
            //
        }
    }
}
