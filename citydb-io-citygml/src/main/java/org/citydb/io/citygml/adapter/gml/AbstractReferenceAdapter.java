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
import org.xmlobjects.gml.model.base.AbstractReference;

public abstract class AbstractReferenceAdapter<T extends AbstractReference<?>> implements ModelSerializer<FeatureProperty, T> {

    @Override
    public void serialize(FeatureProperty source, T target, ModelSerializerHelper helper) throws ModelSerializeException {
        source.getObject()
                .map(Feature::getObjectId)
                .orElseGet(source::getReference)
                .ifPresent(href -> target.setHref("#" + href));
    }
}
