/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.transportation;

import org.citydb.io.citygml.annotation.DatabaseType;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.feature.FeatureType;
import org.citydb.model.property.FeatureProperty;
import org.citygml4j.core.model.transportation.IntersectionProperty;
import org.citygml4j.core.model.transportation.Road;
import org.citygml4j.core.model.transportation.SectionProperty;

@DatabaseType(name = "Road", namespace = Namespaces.TRANSPORTATION)
public class RoadAdapter extends AbstractTransportationSpaceAdapter<Road> {

    @Override
    public Feature createModel(Road source) throws ModelBuildException {
        return Feature.of(FeatureType.ROAD);
    }

    @Override
    public void build(Road source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.TRANSPORTATION);

        if (source.isSetSections()) {
            for (SectionProperty property : source.getSections()) {
                helper.addContainedFeature(Name.of("section", Namespaces.TRANSPORTATION), property, target);
            }
        }

        if (source.isSetIntersections()) {
            for (IntersectionProperty property : source.getIntersections()) {
                helper.addContainedFeature(Name.of("intersection", Namespaces.TRANSPORTATION), property, target);
            }
        }
    }

    @Override
    public Road createObject(Feature source) throws ModelSerializeException {
        return new Road();
    }

    @Override
    public void serialize(Feature source, Road target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.TRANSPORTATION);

        for (FeatureProperty property : source.getFeatures().get(Name.of("section", Namespaces.TRANSPORTATION))) {
            target.getSections().add(helper.getObjectProperty(property, SectionPropertyAdapter.class));
        }

        for (FeatureProperty property : source.getFeatures().get(Name.of("intersection", Namespaces.TRANSPORTATION))) {
            target.getIntersections().add(helper.getObjectProperty(property, IntersectionPropertyAdapter.class));
        }
    }
}
