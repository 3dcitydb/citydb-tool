/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
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

package org.citydb.model.util;

import org.citydb.model.appearance.Texture;
import org.citydb.model.appearance.TextureImageProperty;
import org.citydb.model.common.ExternalFile;
import org.citydb.model.feature.FeatureCollection;
import org.citydb.model.geometry.ImplicitGeometry;
import org.citydb.model.walker.ModelWalker;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

public class ModelWriter {
    private boolean failFast;
    private boolean copyExternalFiles = true;
    private boolean createUniqueFileNames;
    private String textureFolder;
    private String libraryObjectsFolder;

    private ModelWriter() {
    }

    public static ModelWriter newInstance() {
        return new ModelWriter();
    }

    public ModelWriter failFast(boolean failFast) {
        this.failFast = failFast;
        return this;
    }

    public ModelWriter copyExternalFiles(boolean copyExternalFiles) {
        this.copyExternalFiles = copyExternalFiles;
        return this;
    }

    public ModelWriter createUniqueFileNames(boolean createUniqueFileNames) {
        this.createUniqueFileNames = createUniqueFileNames;
        return this;
    }

    public ModelWriter withRelativeTextureFolder(String textureFolder) {
        this.textureFolder = textureFolder;
        return this;
    }

    public ModelWriter withRelativeLibraryObjectsFolder(String libraryObjectsFolder) {
        this.libraryObjectsFolder = libraryObjectsFolder;
        return this;
    }

    public void write(FeatureCollection collection, Path outputFile) throws IOException {
        Objects.requireNonNull(collection, "The feature collection must not be null.");
        outputFile = Objects.requireNonNull(outputFile, "The output file must not be null.")
                .normalize()
                .toAbsolutePath();

        try (ObjectOutputStream stream = new ObjectOutputStream(new BufferedOutputStream(
                Files.newOutputStream(outputFile)))) {
            Preprocessor preprocessor = new Preprocessor(outputFile);
            collection.getFeatures().forEach(feature -> feature.accept(preprocessor));
            stream.writeObject(collection);
        }
    }

    private class Preprocessor extends ModelWalker {
        private final FileHelper textureHelper;
        private final FileHelper libraryObjectsHelper;

        Preprocessor(Path outputFile) {
            this.textureHelper = new FileHelper(textureFolder, "appearance", outputFile, "tex_");
            this.libraryObjectsHelper = new FileHelper(libraryObjectsFolder, "library-objects", outputFile, "lib_");
        }

        @Override
        public void visit(ImplicitGeometry implicitGeometry) {
            implicitGeometry.getLibraryObject()
                    .flatMap(ExternalFile::getPath)
                    .ifPresent(path -> implicitGeometry.setLibraryObject(
                            ExternalFile.of(libraryObjectsHelper.getOrCopy(path))));

            super.visit(implicitGeometry);
        }

        @Override
        public void visit(Texture<?> texture) {
            texture.getTextureImageProperty()
                    .flatMap(property -> property.getObject().flatMap(ExternalFile::getPath))
                    .ifPresent(path -> texture.setTextureImageProperty(TextureImageProperty.of(
                            ExternalFile.of(textureHelper.getOrCopy(path)))));

            super.visit(texture);
        }
    }

    private class FileHelper {
        private final String prefix;
        private final String folderName;
        private final Path outputFolder;
        private boolean isCreated;
        private long counter;

        FileHelper(String userFolder, String defaultFolder, Path outputFile, String prefix) {
            this.prefix = prefix;
            folderName = userFolder != null ? userFolder : defaultFolder;
            outputFolder = outputFile.resolveSibling(folderName);
            isCreated = Files.exists(outputFolder);
        }

        String getOrCopy(Path source) {
            if (copyExternalFiles || source.getFileSystem() != FileSystems.getDefault()) {
                if (!isCreated) {
                    try {
                        Files.createDirectories(outputFolder);
                        isCreated = true;
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to create output directory " + outputFolder + ".", e);
                    }
                }

                try {
                    String fileName = getFileName(source);
                    Files.copy(source, outputFolder.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
                    return folderName + "/" + fileName;
                } catch (Exception e) {
                    if (failFast) {
                        throw new RuntimeException("Failed to copy external file " + source + ".", e);
                    }
                }
            }

            return source.toString();
        }

        String getFileName(Path source) {
            String fileName = source.getFileName().toString();
            if (createUniqueFileNames) {
                String extension = getExtension(fileName);
                fileName = prefix + (counter++);
                if (extension != null) {
                    fileName += extension;
                }
            }

            return fileName;
        }

        String getExtension(String fileName) {
            int index = fileName.lastIndexOf(".");
            return index > 0 ? fileName.substring(index) : null;
        }
    }
}
