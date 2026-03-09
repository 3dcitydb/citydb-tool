/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.core.file;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;

public class FileLocator {
    private final URL url;
    private final Path path;

    private FileLocator(URL url) {
        this.url = Objects.requireNonNull(url, "The URL must not be null.");
        path = null;
    }

    private FileLocator(Path path) {
        this.path = Objects.requireNonNull(path, "The path must not be null.")
                .toAbsolutePath()
                .normalize();
        url = null;
    }

    public static FileLocator of(URL url) {
        return new FileLocator(url);
    }

    public static FileLocator of(Path path) {
        return new FileLocator(path);
    }

    public static FileLocator of(String location) {
        URL url = parseURL(location);
        return url != null ?
                new FileLocator(url) :
                new FileLocator(Path.of(location));
    }

    public static FileLocator of(InputFile file, String location) throws IOException {
        URL url = parseURL(location);
        if (url != null) {
            return FileLocator.of(url);
        }

        if ("/".equals(file.getSeparator())) {
            location = location.replace("\\", "/");
        }

        Path path = null;
        try {
            path = file.resolve(location);
        } catch (Exception e) {
            //
        }

        if (file.getFileType() == FileType.ARCHIVE && (path == null || !Files.exists(path))) {
            try {
                path = file.getFile().getParent().resolve(location);
            } catch (Exception e) {
                //
            }
        }

        if (path == null) {
            throw new IOException("Failed to find file at " + location + ".");
        }

        return FileLocator.of(path);
    }

    public Optional<URL> getURL() {
        return Optional.ofNullable(url);
    }

    public Optional<Path> getPath() {
        return Optional.ofNullable(path);
    }

    public InputStream openStream() throws IOException {
        return new BufferedInputStream(url != null ?
                url.openStream() :
                Files.newInputStream(path));
    }

    public String getFileLocation() {
        return path != null ? path.toString() : url.toExternalForm();
    }

    public String getFileName() {
        return path != null ?
                path.getFileName().toString() :
                Paths.get(url.getPath()).getFileName().toString();
    }

    private static URL parseURL(String location) {
        if (location.indexOf("://") > 0) {
            try {
                return new URL(location);
            } catch (Exception e) {
                //
            }
        }

        return null;
    }

    @Override
    public String toString() {
        return url != null ? url.toExternalForm() : path.toString();
    }
}
