/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
