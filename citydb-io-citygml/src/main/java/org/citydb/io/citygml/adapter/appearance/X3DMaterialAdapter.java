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

import org.citydb.io.citygml.annotation.DatabaseType;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.appearance.Color;
import org.citydb.model.appearance.X3DMaterial;
import org.citydb.model.common.Namespaces;

@DatabaseType(name = "X3DMaterial", namespace = Namespaces.APPEARANCE)
public class X3DMaterialAdapter extends SurfaceDataAdapter<X3DMaterial, org.citygml4j.core.model.appearance.X3DMaterial> {

    @Override
    public X3DMaterial createModel(org.citygml4j.core.model.appearance.X3DMaterial source) {
        return X3DMaterial.newInstance();
    }

    @Override
    public void build(org.citygml4j.core.model.appearance.X3DMaterial source, X3DMaterial target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);

        if (source.isSetShininess()) {
            target.setShininess(source.getShininess());
        }

        if (source.isSetTransparency()) {
            target.setTransparency(source.getTransparency());
        }

        if (source.isSetAmbientIntensity()) {
            target.setAmbientIntensity(source.getAmbientIntensity());
        }

        if (source.isSetIsSmooth()) {
            target.setIsSmooth(source.getIsSmooth());
        }

        if (source.isSetDiffuseColor()) {
            target.setDiffuseColor(Color.of(source.getDiffuseColor().getRed(),
                    source.getDiffuseColor().getGreen(),
                    source.getDiffuseColor().getBlue()));
        }

        if (source.isSetEmissiveColor()) {
            target.setEmissiveColor(Color.of(source.getEmissiveColor().getRed(),
                    source.getEmissiveColor().getGreen(),
                    source.getEmissiveColor().getBlue()));
        }

        if (source.isSetSpecularColor()) {
            target.setSpecularColor(Color.of(source.getSpecularColor().getRed(),
                    source.getSpecularColor().getGreen(),
                    source.getSpecularColor().getBlue()));
        }
    }

    @Override
    public org.citygml4j.core.model.appearance.X3DMaterial createObject(X3DMaterial source) {
        return new org.citygml4j.core.model.appearance.X3DMaterial();
    }

    @Override
    public void serialize(X3DMaterial source, org.citygml4j.core.model.appearance.X3DMaterial target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);

        source.getShininess().ifPresent(target::setShininess);
        source.getTransparency().ifPresent(target::setTransparency);
        source.getAmbientIntensity().ifPresent(target::setAmbientIntensity);
        source.getIsSmooth().ifPresent(target::setIsSmooth);

        source.getDiffuseColor().ifPresent(color -> target.setDiffuseColor(
                new org.citygml4j.core.model.appearance.Color(color.getRed(), color.getGreen(), color.getBlue())));

        source.getEmissiveColor().ifPresent(color -> target.setEmissiveColor(
                new org.citygml4j.core.model.appearance.Color(color.getRed(), color.getGreen(), color.getBlue())));

        source.getSpecularColor().ifPresent(color -> target.setSpecularColor(
                new org.citygml4j.core.model.appearance.Color(color.getRed(), color.getGreen(), color.getBlue())));
    }
}
