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

package org.citydb.core.file;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
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
                .normalize()
                .toAbsolutePath();
        url = null;
    }

    public static FileLocator of(URL url) {
        return new FileLocator(url);
    }

    public static FileLocator of(Path path) {
        return new FileLocator(path);
    }

    public static FileLocator of(String location) {
        try {
            return FileLocator.of(new URL(location));
        } catch (Exception e) {
            return new FileLocator(Path.of(location));
        }
    }

    public static FileLocator of(InputFile file, String location) throws IOException {
        try {
            return FileLocator.of(new URL(location));
        } catch (Exception e) {
            //
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

        if (path == null || !Files.exists(path)) {
            throw new IOException("Failed to find file at " + location + ".");
        } else if (Files.size(path) == 0) {
            throw new IOException("The file " + location + " has zero bytes.");
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

    public String getFileName() {
        return path != null ? path.getFileName().toString() : url.getFile();
    }

    @Override
    public String toString() {
        return url != null ? url.toExternalForm() : path.toString();
    }
}
