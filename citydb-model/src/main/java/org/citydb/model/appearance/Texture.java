/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
