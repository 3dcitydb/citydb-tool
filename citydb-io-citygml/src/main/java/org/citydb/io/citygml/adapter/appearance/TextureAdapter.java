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

package org.citydb.io.citygml.adapter.appearance;

import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.appearance.*;
import org.citydb.model.common.ExternalFile;
import org.citydb.model.common.Reference;
import org.citygml4j.core.model.appearance.AbstractTexture;
import org.citygml4j.core.model.appearance.ColorPlusOpacity;
import org.citygml4j.core.model.core.ImplicitGeometry;
import org.slf4j.event.Level;

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
                String token = source.getParent(ImplicitGeometry.class) != null ? "[template]" : null;
                if (helper.lookupAndPut(textureImage, token)) {
                    target.setTextureImageProperty(TextureImageProperty.of(Reference.of(
                            textureImage.getOrCreateObjectId())));
                } else {
                    if (source.getMimeType() != null && source.getMimeType().getValue() != null) {
                        textureImage.setMimeType(source.getMimeType().getValue());
                        textureImage.setMimeTypeCodeSpace(source.getMimeType().getCodeSpace());
                    }

                    target.setTextureImageProperty(TextureImageProperty.of(textureImage));
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

        source.getTextureImageProperty().ifPresent(property -> {
            ExternalFile textureImage = property.getObject()
                    .orElseGet(() -> helper.lookupExternalFile(property.getReference()
                            .map(Reference::getTarget)
                            .orElse(null)));
            if (textureImage != null) {
                target.setImageURI(textureImage.getFileLocation());
            }
        });
    }
}
