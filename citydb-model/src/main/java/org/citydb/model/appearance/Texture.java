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

package org.citydb.model.appearance;

import org.citydb.model.common.ExternalFile;

import java.util.Optional;

public abstract class Texture<T extends Texture<?>> extends SurfaceData<T> {
    private ExternalFile textureImage;
    private TextureType textureType;
    private WrapMode wrapMode;
    private Color borderColor;

    public Optional<ExternalFile> getTextureImage() {
        return Optional.ofNullable(textureImage);
    }

    public T setTextureImage(ExternalFile textureImage) {
        this.textureImage = textureImage;
        return self();
    }

    public Optional<TextureType> getTextureType() {
        return Optional.ofNullable(textureType);
    }

    public T setTextureType(TextureType textureType) {
        this.textureType = textureType;
        return self();
    }

    public Optional<WrapMode> getWrapMode() {
        return Optional.ofNullable(wrapMode);
    }

    public T setWrapMode(WrapMode wrapMode) {
        this.wrapMode = wrapMode;
        return self();
    }

    public Optional<Color> getBorderColor() {
        return Optional.ofNullable(borderColor);
    }

    public T setBorderColor(Color borderColor) {
        this.borderColor = borderColor;
        return self();
    }
}
