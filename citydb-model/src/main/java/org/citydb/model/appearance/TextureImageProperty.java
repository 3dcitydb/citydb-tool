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

package org.citydb.model.appearance;

import org.citydb.model.common.ExternalFile;
import org.citydb.model.common.InlineOrByReferenceProperty;
import org.citydb.model.common.Reference;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

public class TextureImageProperty implements InlineOrByReferenceProperty<ExternalFile>, Serializable {
    private ExternalFile textureImage;
    private Reference reference;

    private TextureImageProperty(ExternalFile textureImage) {
        setObject(Objects.requireNonNull(textureImage, "The texture image must not be null."));
    }

    private TextureImageProperty(Reference reference) {
        setReference(Objects.requireNonNull(reference, "The reference must not be null."));
    }

    public static TextureImageProperty of(ExternalFile textureImage) {
        return new TextureImageProperty(textureImage);
    }

    public static TextureImageProperty of(Reference reference) {
        return new TextureImageProperty(reference);
    }

    @Override
    public Optional<ExternalFile> getObject() {
        return Optional.ofNullable(textureImage);
    }

    @Override
    public TextureImageProperty setObject(ExternalFile textureImage) {
        if (textureImage != null) {
            this.textureImage = textureImage;
            reference = null;
        }

        return this;
    }

    @Override
    public Optional<Reference> getReference() {
        return Optional.ofNullable(reference);
    }

    @Override
    public TextureImageProperty setReference(Reference reference) {
        if (reference != null) {
            this.reference = reference;
            textureImage = null;
        }

        return this;
    }
}
