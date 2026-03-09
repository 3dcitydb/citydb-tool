/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.gml;

import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.serializer.ModelSerializer;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.feature.Feature;
import org.citydb.model.property.FeatureProperty;
import org.xmlobjects.gml.model.base.AbstractGML;
import org.xmlobjects.gml.model.base.AbstractInlineOrByReferenceProperty;

public abstract class AbstractInlineOrByReferencePropertyAdapter<T extends AbstractInlineOrByReferenceProperty<?>> implements ModelSerializer<FeatureProperty, T> {

    @Override
    public void serialize(FeatureProperty source, T target, ModelSerializerHelper helper) throws ModelSerializeException {
        Feature feature = source.getObject().orElse(null);
        if (feature != null) {
            if (helper.lookupAndPut(feature)) {
                target.setHref("#" + feature.getOrCreateObjectId());
            } else {
                target.setInlineObjectIfValid(helper.getObject(feature, AbstractGML.class));
            }
        } else {
            source.getReference().ifPresent(reference -> target.setHref("#" + reference));
        }
    }
}
