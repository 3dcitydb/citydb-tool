/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.dynamizer;

import org.citydb.io.citygml.annotation.DatabaseType;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.feature.FeatureType;
import org.citydb.model.property.Attribute;
import org.citygml4j.core.model.dynamizer.CompositeTimeseries;
import org.citygml4j.core.model.dynamizer.TimeseriesComponentProperty;

@DatabaseType(name = "CompositeTimeseries", namespace = Namespaces.DYNAMIZER)
public class CompositeTimeseriesAdapter extends AbstractTimeseriesAdapter<CompositeTimeseries> {

    @Override
    public Feature createModel(CompositeTimeseries source) throws ModelBuildException {
        return Feature.of(FeatureType.COMPOSITE_TIMESERIES);
    }

    @Override
    public void build(CompositeTimeseries source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);

        if (source.isSetComponents()) {
            for (TimeseriesComponentProperty property : source.getComponents()) {
                if (property != null) {
                    helper.addAttribute(Name.of("component", Namespaces.DYNAMIZER), property.getObject(), target,
                            TimeseriesComponentAdapter.class);
                }
            }
        }
    }

    @Override
    public CompositeTimeseries createObject(Feature source) throws ModelSerializeException {
        return new CompositeTimeseries();
    }

    @Override
    public void serialize(Feature source, CompositeTimeseries target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);

        for (Attribute attribute : source.getAttributes().get(Name.of("component", Namespaces.DYNAMIZER))) {
            target.getComponents().add(new TimeseriesComponentProperty(
                    helper.getAttribute(attribute, TimeseriesComponentAdapter.class)));
        }
    }
}
