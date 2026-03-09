/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.core;

import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.feature.Feature;
import org.citygml4j.core.model.core.AbstractFeatureWithLifespan;

public abstract class AbstractFeatureWithLifespanAdapter<T extends AbstractFeatureWithLifespan> extends AbstractFeatureAdapter<T> {

    @Override
    public void build(T source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);
        target.setCreationDate(source.getCreationDate())
                .setTerminationDate(source.getTerminationDate())
                .setValidFrom(source.getValidFrom())
                .setValidTo(source.getValidTo());
    }

    @Override
    public void serialize(Feature source, T target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);
        boolean isTopLevel = source.getParent().isEmpty();

        if (isTopLevel) {
            source.getCreationDate().ifPresent(target::setCreationDate);
            source.getTerminationDate().ifPresent(target::setTerminationDate);
            source.getValidFrom().ifPresent(target::setValidFrom);
            source.getValidTo().ifPresent(target::setValidTo);
        }
    }
}
