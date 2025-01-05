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

package org.citydb.core.cache;

import org.h2.mvstore.MVStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Map;

public class PersistentMapStore implements AutoCloseable {
    private MVStore store;
    private final Path backingFile;

    public enum CompressionLevel {
        DISABLED,
        FAST,
        HIGH
    }

    private PersistentMapStore(MVStore store, Path backingFile) {
        this.store = store;
        this.backingFile = backingFile;
    }

    public static PersistentMapStore newInstance() throws IOException {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Path getBackingFile() {
        ensureOpen();
        return backingFile;
    }

    public boolean hasMap(String name) {
        return store != null && store.hasMap(name);
    }

    public <K, V> Map<K, V> getOrCreateMap(String name) {
        ensureOpen();
        return store.openMap(name);
    }

    public void removeMap(String name) {
        if (store != null) {
            store.removeMap(name);
        }
    }

    public boolean isClosed() {
        return store == null || store.isClosed();
    }

    @Override
    public void close() {
        if (store != null) {
            try {
                store.closeImmediately();
                Files.deleteIfExists(backingFile);
            } catch (Exception e) {
                //
            } finally {
                store = null;
            }
        }
    }

    private void ensureOpen() {
        if (store == null) {
            throw new IllegalStateException("Cache store is closed.");
        }
    }

    public static class Builder {
        private static final SecureRandom random = new SecureRandom();
        private Path tempDirectory;
        private CompressionLevel compressionLevel;
        private int cacheSize;
        private int cacheConcurrency;

        private Builder() {
        }

        public Builder tempDirectory(Path tempDirectory) {
            this.tempDirectory = tempDirectory;
            return this;
        }

        public Builder compressionLevel(CompressionLevel compressionLevel) {
            this.compressionLevel = compressionLevel;
            return this;
        }

        public Builder cacheSize(int cacheSize) {
            if (cacheSize > 0) {
                this.cacheSize = cacheSize;
            }

            return this;
        }

        public Builder cacheConcurrency(int cacheConcurrency) {
            if (cacheConcurrency > 0) {
                this.cacheConcurrency = cacheConcurrency;
            }

            return this;
        }

        public PersistentMapStore build() throws IOException {
            try {
                Path backingFile = getBackingFile();
                MVStore store = getBuilder()
                        .fileName(backingFile.toString())
                        .open();

                return new PersistentMapStore(store, backingFile);
            } catch (Exception e) {
                throw new IOException("Failed to create persistent map store.", e);
            }
        }

        private MVStore.Builder getBuilder() {
            MVStore.Builder builder = new MVStore.Builder();

            if (compressionLevel == null || compressionLevel == CompressionLevel.FAST) {
                builder.compress();
            } else if (compressionLevel == CompressionLevel.HIGH) {
                builder.compressHigh();
            }

            if (cacheSize > 0) {
                builder.cacheSize(cacheSize);
            }

            if (cacheConcurrency > 0) {
                builder.cacheConcurrency(cacheConcurrency);
            }

            return builder;
        }

        private Path getBackingFile() throws IOException {
            if (tempDirectory == null) {
                tempDirectory = Path.of(System.getProperty("java.io.tmpdir"), "citydb");
            }

            if (!Files.exists(tempDirectory)) {
                Files.createDirectories(tempDirectory);
            } else if (!Files.isDirectory(tempDirectory)) {
                throw new IOException("The persistent map store location " + tempDirectory + " is not a directory.");
            }

            long n = random.nextLong() ^ System.currentTimeMillis();
            Path backingFile = tempDirectory.resolve("cache-" + Long.toUnsignedString(n) + ".tmp")
                    .normalize()
                    .toAbsolutePath();

            Files.deleteIfExists(backingFile);
            return backingFile;
        }
    }
}
