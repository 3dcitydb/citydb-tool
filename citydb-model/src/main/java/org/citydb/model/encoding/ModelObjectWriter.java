/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
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

package org.citydb.model.encoding;

import org.citydb.model.appearance.Texture;
import org.citydb.model.common.Child;
import org.citydb.model.common.Visitable;
import org.citydb.model.geometry.ImplicitGeometry;
import org.citydb.model.walker.ModelWalker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class ModelObjectWriter {
    public static final String TEXTURE_DIR = "appearance";
    public static final String TEXTURE_PREFIX = "tex_";
    public static final String LIBRARY_OBJECTS_DIR = "library-objects";
    public static final String LIBRARY_OBJECTS_PREFIX = "lib_";

    private final Object lock = new Object();
    private final FileHelper helper;

    private ModelObjectWriter(Builder builder) {
        helper = new FileHelper(builder);
    }

    public static ModelObjectWriter newInstance() {
        return new Builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public void write(Child object, Path file) throws IOException {
        write(Collections.singletonList(object), file);
    }

    public void write(List<Child> objects, Path file) throws IOException {
        file = file.toAbsolutePath().normalize();
        helper.ensureCreated(file.getParent());
        try (ObjectOutputStream stream = new ObjectOutputStream(new BufferedOutputStream(
                Files.newOutputStream(file)))) {
            for (Child object : objects) {
                writeObject(object, stream, file.getParent());
            }
        }
    }

    public void write(Child object, ObjectOutputStream stream, Path baseDir) throws IOException {
        write(Collections.singletonList(object), stream, baseDir);
    }

    public void write(List<Child> objects, ObjectOutputStream stream, Path baseDir) throws IOException {
        baseDir = baseDir.toAbsolutePath().normalize();
        helper.ensureCreated(baseDir);
        for (Child object : objects) {
            writeObject(object, stream, baseDir);
        }
    }

    private void writeObject(Child object, ObjectOutputStream stream, Path baseDir) throws IOException {
        if (object instanceof Visitable visitable) {
            try {
                visitable.accept(new Processor(baseDir, helper));
            } catch (UncheckedIOException e) {
                throw new IOException(e.getMessage(), e.getCause());
            }
        }

        synchronized (lock) {
            stream.writeUnshared(object);
        }
    }

    private static class Processor extends ModelWalker {
        private final Path outputFolder;
        private final FileHelper helper;
        private final Map<Path, Path> copied = new HashMap<>();

        Processor(Path outputFolder, FileHelper helper) {
            this.outputFolder = outputFolder;
            this.helper = helper;
        }

        @Override
        public void visit(ImplicitGeometry implicitGeometry) {
            implicitGeometry.getLibraryObject()
                    .ifPresent(libraryObject -> libraryObject.getPath()
                            .ifPresent(path -> libraryObject.setPath(copied.computeIfAbsent(path,
                                    k -> helper.copyLibraryObject(path, outputFolder)))));

            super.visit(implicitGeometry);
        }

        @Override
        public void visit(Texture<?> texture) {
            texture.getTextureImage()
                    .ifPresent(textureImage -> textureImage.getPath()
                            .ifPresent(path -> textureImage.setPath(copied.computeIfAbsent(path,
                                    k -> helper.copyTexture(path, outputFolder)))));

            super.visit(texture);
        }
    }

    private static class FileHelper {
        private final Logger logger = LoggerFactory.getLogger(FileHelper.class);
        private final Set<Path> createdDirs = ConcurrentHashMap.newKeySet();
        private final AtomicLong textureCounter = new AtomicLong();
        private final AtomicLong libraryObjectsCounter = new AtomicLong();

        private final boolean copyExternalFiles;
        private final int buckets;
        private final boolean useBuckets;

        FileHelper(Builder builder) {
            this.copyExternalFiles = builder.copyExternalFiles;
            this.buckets = builder.buckets;
            useBuckets = buckets > 0;
        }

        Path copyTexture(Path source, Path outputFolder) {
            return copy(source, outputFolder.resolve(TEXTURE_DIR), TEXTURE_PREFIX, textureCounter);
        }

        Path copyLibraryObject(Path source, Path outputFolder) {
            return copy(source, outputFolder.resolve(LIBRARY_OBJECTS_DIR), LIBRARY_OBJECTS_PREFIX,
                    libraryObjectsCounter);
        }

        private Path copy(Path source, Path outputFolder, String prefix, AtomicLong counter) {
            if (!copyExternalFiles && source.getFileSystem() == FileSystems.getDefault()) {
                return source;
            }

            long id = counter.incrementAndGet();
            Path targetFolder = useBuckets ?
                    outputFolder.resolve(String.valueOf(Math.abs((id - 1) % buckets) + 1)) :
                    outputFolder;

            try {
                ensureCreated(targetFolder);
                Path target = targetFolder.resolve(getFileName(source, id, prefix));
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                return target;
            } catch (NoSuchFileException e) {
                logger.debug("Failed to copy external resource file {}.", source, e);
                return source;
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to copy external file " + source + ".", e);
            }
        }

        private String getFileName(Path source, long id, String prefix) {
            String extension = getExtension(source.getFileName().toString());
            String fileName = prefix + id;
            if (extension != null) {
                fileName += extension;
            }

            return fileName;
        }

        private String getExtension(String fileName) {
            int index = fileName.lastIndexOf(".");
            return index > 0 ? fileName.substring(index) : null;
        }

        void ensureCreated(Path dir) throws IOException {
            if (dir != null) {
                dir = dir.toAbsolutePath().normalize();
                if (createdDirs.add(dir)) {
                    Files.createDirectories(dir);
                }
            }
        }
    }

    public static class Builder {
        private boolean copyExternalFiles;
        private int buckets = 10;

        private Builder() {
        }

        public Builder copyExternalFiles(boolean copyExternalFiles) {
            this.copyExternalFiles = copyExternalFiles;
            return this;
        }

        public Builder withBuckets(int buckets) {
            this.buckets = buckets;
            return this;
        }

        public ModelObjectWriter build() {
            return new ModelObjectWriter(this);
        }
    }
}
