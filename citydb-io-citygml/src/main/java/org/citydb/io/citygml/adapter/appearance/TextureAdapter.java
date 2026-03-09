/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.appearance;

import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.appearance.Color;
import org.citydb.model.appearance.Texture;
import org.citydb.model.appearance.TextureType;
import org.citydb.model.appearance.WrapMode;
import org.citydb.model.common.ExternalFile;
import org.citygml4j.core.model.appearance.AbstractTexture;
import org.citygml4j.core.model.appearance.ColorPlusOpacity;
import org.slf4j.event.Level;
import org.xmlobjects.gml.model.basictypes.Code;

import java.io.IOException;

public abstract class TextureAdapter<T extends Texture<?>, R extends AbstractTexture> extends SurfaceDataAdapter<T, R> {

    @Override
    public void build(R source, T target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);

        if (source.getTextureType() != null) {
            target.setTextureType(switch (source.getTextureType()) {
                case SPECIFIC -> TextureType.SPECIFIC;
                case TYPICAL -> TextureType.TYPICAL;
                case UNKNOWN -> TextureType.UNKNOWN;
            });
        }

        if (source.getWrapMode() != null) {
            target.setWrapMode(switch (source.getWrapMode()) {
                case NONE -> WrapMode.NONE;
                case WRAP -> WrapMode.WRAP;
                case CLAMP -> WrapMode.CLAMP;
                case BORDER -> WrapMode.BORDER;
                case MIRROR -> WrapMode.MIRROR;
            });
        }

        if (source.getBorderColor() != null) {
            target.setBorderColor(Color.of(source.getBorderColor().getRed(),
                    source.getBorderColor().getGreen(),
                    source.getBorderColor().getBlue(),
                    source.getBorderColor().getOpacity()));
        }

        if (source.getImageURI() != null) {
            try {
                ExternalFile textureImage = helper.getExternalFile(source.getImageURI());
                target.setTextureImage(textureImage);

                if (source.getMimeType() != null && source.getMimeType().getValue() != null) {
                    textureImage.setMimeType(source.getMimeType().getValue());
                    textureImage.setMimeTypeCodeSpace(source.getMimeType().getCodeSpace());
                }
            } catch (IOException e) {
                helper.logOrThrow(Level.ERROR, helper.formatMessage(source, "Failed to read texture image file " +
                        source.getImageURI() + "."), e);
            }
        }
    }

    @Override
    public void serialize(T source, R target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);

        source.getTextureType().ifPresent(value -> target.setTextureType(
                org.citygml4j.core.model.appearance.TextureType.fromValue(value.getDatabaseValue())));

        source.getWrapMode().ifPresent(value -> target.setWrapMode(
                org.citygml4j.core.model.appearance.WrapMode.fromValue(value.getDatabaseValue())));

        source.getBorderColor().ifPresent(color -> target.setBorderColor(
                new ColorPlusOpacity(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha())));

        source.getTextureImage().ifPresent(textureImage -> {
            target.setImageURI(textureImage.getFileLocation());
            textureImage.getMimeType().ifPresent(mimeType -> target.setMimeType(
                    new Code(mimeType, textureImage.getMimeTypeCodeSpace().orElse(null))));
        });
    }
}
