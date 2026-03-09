/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.dynamizer;

import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.property.Attribute;
import org.citydb.model.property.DataType;
import org.citygml4j.core.model.dynamizer.AbstractAtomicTimeseries;

public abstract class AbstractAtomicTimeseriesAdapter<T extends AbstractAtomicTimeseries> extends AbstractTimeseriesAdapter<T> {

    @Override
    public void build(T source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);

        if (source.getObservationProperty() != null) {
            target.addProperty(Attribute.of(Name.of("observationProperty", Namespaces.DYNAMIZER), DataType.STRING)
                    .setStringValue(source.getObservationProperty()));
        }

        if (source.getUom() != null) {
            target.addProperty(Attribute.of(Name.of("uom", Namespaces.DYNAMIZER), DataType.STRING)
                    .setStringValue(source.getUom()));
        }
    }

    @Override
    public void serialize(Feature source, T target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);

        source.getAttributes().getFirst(Name.of("observationProperty", Namespaces.DYNAMIZER))
                .flatMap(Attribute::getStringValue)
                .ifPresent(target::setObservationProperty);

        source.getAttributes().getFirst(Name.of("uom", Namespaces.DYNAMIZER))
                .flatMap(Attribute::getStringValue)
                .ifPresent(target::setUom);
    }
}
