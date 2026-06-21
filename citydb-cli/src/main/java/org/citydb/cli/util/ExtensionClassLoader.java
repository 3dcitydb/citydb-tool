/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.util;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

public class ExtensionClassLoader extends URLClassLoader {

    static {
        ClassLoader.registerAsParallelCapable();
    }

    private ExtensionClassLoader(ClassLoader parent) {
        super(new URL[0], parent);
    }

    public static ExtensionClassLoader newInstance() {
        return new ExtensionClassLoader(Thread.currentThread().getContextClassLoader());
    }

    public static ExtensionClassLoader of(ClassLoader parent) {
        return new ExtensionClassLoader(parent);
    }

    public ExtensionClassLoader loadFrom(Path path) throws IOException {
        findJars(path).forEach(this::addURL);
        return this;
    }

    private List<URL> findJars(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return List.of();
        }

        List<URL> urls = new ArrayList<>();
        Files.walkFileTree(path, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        if (attrs.isRegularFile()
                                && file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                            urls.add(file.toUri().toURL());
                        }

                        return FileVisitResult.CONTINUE;
                    }
                });

        return urls;
    }
}
