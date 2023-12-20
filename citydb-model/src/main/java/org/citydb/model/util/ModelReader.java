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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class ModelReader {

    private ModelReader() {
    }

    public static ModelReader newInstance() {
        return new ModelReader();
    }

    public FeatureCollection read(Path inputFile) throws IOException {
        Objects.requireNonNull(inputFile, "The input file must not be null.");

        try (ObjectInputStream stream = new ObjectInputStream(new BufferedInputStream(
                Files.newInputStream(inputFile)))) {
            Object object = stream.readObject();
            if (!(object instanceof FeatureCollection collection)) {
                throw new IOException("The input file " + inputFile + " is not a feature collection.");
            }

            Postprocessor postprocessor = new Postprocessor(inputFile);
            collection.getFeatures().forEach(feature -> feature.accept(postprocessor));
            return collection;
        } catch (ClassNotFoundException e) {
            throw new IOException("Failed to read feature collection.", e);
        }
    }

    private static class Postprocessor extends ModelWalker {
        private final Path inputFile;

        Postprocessor(Path inputFile) {
            this.inputFile = inputFile;
        }

        @Override
        public void visit(ImplicitGeometry implicitGeometry) {
            implicitGeometry.getLibraryObject()
                    .ifPresent(externalFile -> implicitGeometry.setLibraryObject(getOrCreate(externalFile)));

            super.visit(implicitGeometry);
        }

        @Override
        public void visit(Texture<?> texture) {
            texture.getTextureImageProperty()
                    .flatMap(TextureImageProperty::getObject)
                    .ifPresent(externalFile -> texture.setTextureImageProperty(
                            TextureImageProperty.of(getOrCreate(externalFile))));

            super.visit(texture);
        }

        private ExternalFile getOrCreate(ExternalFile candidate) {
            String uri = candidate.getURI().orElse(null);
            if (uri != null) {
                try {
                    Path path = inputFile.resolveSibling(uri);
                    if (Files.exists(path)) {
                        return ExternalFile.of(path);
                    }
                } catch (Exception e) {
                    //
                }
            }

            return candidate;
        }
    }
}
