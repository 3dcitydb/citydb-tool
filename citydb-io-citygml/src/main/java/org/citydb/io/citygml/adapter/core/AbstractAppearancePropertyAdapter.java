/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.core;

import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.serializer.ModelSerializer;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.property.AppearanceProperty;
import org.citygml4j.core.model.core.AbstractAppearanceProperty;

public class AbstractAppearancePropertyAdapter implements ModelSerializer<AppearanceProperty, AbstractAppearanceProperty> {

    @Override
    public AbstractAppearanceProperty createObject(AppearanceProperty source) throws ModelSerializeException {
        return new AbstractAppearanceProperty();
    }

    @Override
    public void serialize(AppearanceProperty source, AbstractAppearanceProperty target, ModelSerializerHelper helper) throws ModelSerializeException {
        target.setInlineObjectIfValid(helper.getAppearance(source.getObject()));
    }
}
