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

package org.citydb.io.citygml.adapter.appearance.builder;

import org.citydb.io.citygml.adapter.appearance.SurfaceDataAdapter;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.builder.ModelBuilder;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.model.appearance.Appearance;
import org.citydb.model.appearance.SurfaceData;
import org.citydb.model.appearance.SurfaceDataProperty;
import org.citydb.model.common.Reference;
import org.citygml4j.core.model.appearance.AbstractSurfaceData;
import org.citygml4j.core.model.appearance.AbstractSurfaceDataProperty;

public class AppearanceBuilder implements ModelBuilder<org.citygml4j.core.model.appearance.Appearance, Appearance> {
    private final AppearanceHelper appearanceHelper;

    AppearanceBuilder(AppearanceHelper appearanceHelper) {
        this.appearanceHelper = appearanceHelper;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void build(org.citygml4j.core.model.appearance.Appearance source, Appearance target, ModelBuilderHelper helper) throws ModelBuildException {
        target.setObjectId(source.getId())
                .setTheme(source.getTheme())
                .setCreationDate(source.getCreationDate())
                .setTerminationDate(source.getTerminationDate())
                .setValidFrom(source.getValidFrom())
                .setValidTo(source.getValidTo());

        if (source.getIdentifier() != null && source.getIdentifier().getValue() != null) {
            target.setIdentifier(source.getIdentifier().getValue())
                    .setIdentifierCodeSpace(source.getIdentifier().getCodeSpace());
        }

        if (source.isSetSurfaceData()) {
            for (AbstractSurfaceDataProperty property : source.getSurfaceData()) {
                if (property != null) {
                    if (property.isSetInlineObject()) {
                        SurfaceDataAdapter<SurfaceData<?>, AbstractSurfaceData> builder = helper.getContext()
                                .getBuilderByType(property.getObject().getClass(), SurfaceDataAdapter.class);
                        if (builder != null) {
                            SurfaceData<?> surfaceData = builder.createModel(property.getObject());
                            if (surfaceData != null) {
                                builder.build(property.getObject(), surfaceData, helper);
                                target.getSurfaceData().add(SurfaceDataProperty.of(surfaceData));
                                appearanceHelper.addSurfaceData(surfaceData, property.getObject());
                            } else {
                                throw new ModelBuildException("The builder " + builder.getClass().getName() +
                                        " returned a null object.");
                            }
                        }
                    } else {
                        Reference reference = helper.getFeatureReference(property);
                        if (reference != null) {
                            target.getSurfaceData().add(SurfaceDataProperty.of(reference));
                        }
                    }
                }
            }
        }
    }
}
