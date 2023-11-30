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

package org.citydb.io.citygml.adapter.appearance.serializer;

import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.serializer.ModelSerializer;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.appearance.SurfaceDataProperty;
import org.citygml4j.core.model.appearance.Appearance;
import org.xmlobjects.gml.model.basictypes.CodeWithAuthority;

public class AppearanceSerializer implements ModelSerializer<org.citydb.model.appearance.Appearance, Appearance> {
    private final AppearanceHelper appearanceHelper;

    AppearanceSerializer(AppearanceHelper appearanceHelper) {
        this.appearanceHelper = appearanceHelper;
    }

    @Override
    public Appearance createObject(org.citydb.model.appearance.Appearance source) throws ModelSerializeException {
        return new org.citygml4j.core.model.appearance.Appearance();
    }

    @Override
    public void serialize(org.citydb.model.appearance.Appearance source, Appearance target, ModelSerializerHelper helper) throws ModelSerializeException {
        source.getObjectId().ifPresent(target::setId);
        source.getTheme().ifPresent(target::setTheme);

        source.getIdentifier().ifPresent(identifier -> target.setIdentifier(
                new CodeWithAuthority(identifier, source.getIdentifierCodeSpace().orElse(null))));

        if (source.hasSurfaceData()) {
            for (SurfaceDataProperty property : source.getSurfaceData()) {
                target.getSurfaceData().add(appearanceHelper.getSurfaceDataProperty(property));
            }
        }
    }
}
