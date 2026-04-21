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
     */
    public static void deleteDirectoryTree(Path root) {
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

        files.parallelStream().forEach(file -> {
            try {
                Files.delete(file);
            } catch (IOException ignored) {
                //
            }
        });

        for (Path dir : dirs) {
            try {
                Files.delete(dir);
            } catch (IOException ignored) {
                //
            }
        }
    }
}
