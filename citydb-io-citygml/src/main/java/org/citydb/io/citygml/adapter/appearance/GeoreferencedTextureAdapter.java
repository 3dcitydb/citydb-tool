/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
 * Virtual City Systems, Germany
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

import org.apache.logging.log4j.Level;
import org.citydb.core.file.FileLocator;
import org.citydb.io.citygml.annotation.DatabaseType;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.appearance.GeoreferencedTexture;
import org.citydb.model.appearance.TextureImageProperty;
import org.citydb.model.common.ExternalFile;
import org.citydb.model.common.Namespaces;
import org.citydb.model.geometry.Point;
import org.citygml4j.core.model.core.TransformationMatrix2x2;
import org.xmlobjects.gml.model.geometry.DirectPosition;
import org.xmlobjects.gml.model.geometry.primitives.PointProperty;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@DatabaseType(name = "GeoreferencedTexture", namespace = Namespaces.APPEARANCE)
public class GeoreferencedTextureAdapter extends TextureAdapter<GeoreferencedTexture, org.citygml4j.core.model.appearance.GeoreferencedTexture> {

    @Override
    public GeoreferencedTexture createModel(org.citygml4j.core.model.appearance.GeoreferencedTexture source) {
        return GeoreferencedTexture.newInstance();
    }

    @Override
    public void build(org.citygml4j.core.model.appearance.GeoreferencedTexture source, GeoreferencedTexture target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);

        if (source.getOrientation() == null
                && source.getReferencePoint() == null
                && source.getImageURI() != null) {
            ExternalFile textureImage = target.getTextureImageProperty()
                    .flatMap(TextureImageProperty::getObject)
                    .orElseGet(() -> ExternalFile.of(source.getImageURI()));
            processWorldFile(source, textureImage, helper);
        }

        if (source.getReferencePoint() != null && source.getReferencePoint().getObject() != null) {
            target.setReferencePoint(helper.getPointGeometry(source.getReferencePoint().getObject(), Point.class));
        }

        if (source.getOrientation() != null) {
            target.setOrientation(source.getOrientation().toRowMajorList());
        }
    }

    @Override
    public org.citygml4j.core.model.appearance.GeoreferencedTexture createObject(GeoreferencedTexture source) {
        return new org.citygml4j.core.model.appearance.GeoreferencedTexture();
    }

    @Override
    public void serialize(GeoreferencedTexture source, org.citygml4j.core.model.appearance.GeoreferencedTexture target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);

        source.getReferencePoint().ifPresent(referencePoint ->
                target.setReferencePoint(new PointProperty(helper.getPoint(referencePoint, false))));

        source.getOrientation().ifPresent(transformationMatrix ->
                target.setOrientation(TransformationMatrix2x2.ofRowMajorList(transformationMatrix)));
    }

    private void processWorldFile(org.citygml4j.core.model.appearance.GeoreferencedTexture source, ExternalFile textureImage, ModelBuilderHelper helper) throws ModelBuildException {
        String imageURI = textureImage.getFileLocation();
        if (imageURI != null) {
            List<String> candidates = new ArrayList<>();
            candidates.add(imageURI + "w");

            int index = imageURI.lastIndexOf(".");
            if (index != -1) {
                String name = imageURI.substring(0, index + 1);
                String extension = imageURI.substring(index + 1);
                if (extension.length() == 3) {
                    candidates.add(name + extension.charAt(0) + extension.charAt(2) + "w");
                }
            }

            for (String candidate : candidates) {
                FileLocator worldFile;
                try {
                    worldFile = FileLocator.of(helper.getInputFile(), candidate);
                } catch (IOException e) {
                    continue;
                }

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(worldFile.openStream()))) {
                    List<Double> content = new ArrayList<>();

                    String line;
                    for (int i = 0; i < 6 && (line = reader.readLine()) != null; i++) {
                        content.add(Double.parseDouble(line));
                    }

                    if (content.size() == 6) {
                        source.setOrientation(TransformationMatrix2x2.ofRowMajorList(content.subList(0, 4)));
                        source.setReferencePoint(new PointProperty(
                                new org.xmlobjects.gml.model.geometry.primitives.Point(
                                        new DirectPosition(content.subList(4, 6)))));
                        break;
                    } else {
                        throw new IOException("World file has unexpected content.");
                    }
                } catch (Exception e) {
                    helper.logOrThrow(Level.ERROR, helper.formatMessage(source, "Failed to parse world file " +
                            worldFile + "."), e);
                }
            }
        }
    }
}
