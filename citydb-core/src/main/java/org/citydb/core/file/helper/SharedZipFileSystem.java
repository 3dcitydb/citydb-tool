/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.core.file.helper;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class SharedZipFileSystem {
    private static final Map<URI, SharedZipFileSystem> instances = new HashMap<>();
    private static final Object instancesLock = new Object();

    private final URI uri;
    private final Object fileSystemLock = new Object();
    private volatile FileSystem fileSystem;
    private int references;

    private SharedZipFileSystem(URI uri) {
        this.uri = Objects.requireNonNull(uri, "The zip file URI must not be null.");
    }

    public static SharedZipFileSystem acquire(URI uri) {
        synchronized (instancesLock) {
            SharedZipFileSystem fileSystem = instances.computeIfAbsent(uri, SharedZipFileSystem::new);
            fileSystem.references++;
            return fileSystem;
        }
    }

    public FileSystem getFileSystem() throws IOException {
        if (fileSystem == null) {
            synchronized (fileSystemLock) {
                if (fileSystem == null) {
                    fileSystem = openFileSystem();
                }
            }
        }

        return fileSystem;
    }

    private FileSystem openFileSystem() throws IOException {
        try {
            return FileSystems.getFileSystem(uri);
        } catch (FileSystemNotFoundException e) {
            return FileSystems.newFileSystem(uri, Collections.emptyMap());
        }
    }

    public void release() throws IOException {
        synchronized (instancesLock) {
            if (--references == 0) {
                instances.remove(uri, this);
                synchronized (fileSystemLock) {
                    if (fileSystem != null) {
                        fileSystem.close();
                        fileSystem = null;
                    }
                }
            }
        }
    }
}
