/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.plugin;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

public class PluginClassLoader extends URLClassLoader {

    static {
        ClassLoader.registerAsParallelCapable();
    }

    PluginClassLoader(ClassLoader parent) {
        super(new URL[0], parent);
    }

    public void addPath(Path searchPath) throws PluginException {
        findJars(searchPath).forEach(this::addURL);
    }

    private List<URL> findJars(Path searchPath) throws PluginException {
        if (searchPath == null || !Files.exists(searchPath)) {
            return List.of();
        }

        try {
            List<URL> urls = new ArrayList<>();
            Files.walkFileTree(searchPath, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
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
        } catch (Exception e) {
            throw new PluginException("Failed to load plugins from " + searchPath + ".", e);
        }
    }
}
