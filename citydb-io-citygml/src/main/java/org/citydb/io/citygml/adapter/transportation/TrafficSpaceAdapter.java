/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
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

package org.citydb.io.citygml.adapter.transportation;

import org.citydb.io.citygml.adapter.core.AbstractUnoccupiedSpaceAdapter;
import org.citydb.io.citygml.adapter.core.OccupancyAdapter;
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
import org.citydb.model.property.DataType;
import org.citydb.model.property.FeatureProperty;
import org.citygml4j.core.model.core.OccupancyProperty;
import org.citygml4j.core.model.transportation.*;

@DatabaseType(name = "TrafficSpace", namespace = Namespaces.TRANSPORTATION)
public class TrafficSpaceAdapter extends AbstractUnoccupiedSpaceAdapter<TrafficSpace> {

    @Override
    public Feature createModel(TrafficSpace source) throws ModelBuildException {
        return Feature.of(FeatureType.TRAFFIC_SPACE);
    }

    @Override
    public void build(TrafficSpace source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.TRANSPORTATION);

        if (source.getGranularity() != null) {
            target.addAttribute(Attribute.of(Name.of("granularity", Namespaces.TRANSPORTATION), DataType.STRING)
                    .setStringValue(source.getGranularity().toValue()));
        }

        if (source.getTrafficDirection() != null) {
            target.addAttribute(Attribute.of(Name.of("trafficDirection", Namespaces.TRANSPORTATION), DataType.STRING)
                    .setStringValue(source.getTrafficDirection().toValue()));
        }

        if (source.isSetOccupancies()) {
            for (OccupancyProperty property : source.getOccupancies()) {
                if (property != null) {
                    helper.addAttribute(Name.of("occupancy", Namespaces.TRANSPORTATION), property.getObject(), target,
                            OccupancyAdapter.class);
                }
            }
        }

        if (source.isSetPredecessors()) {
            for (TrafficSpaceReference property : source.getPredecessors()) {
                helper.addRelatedFeature(Name.of("predecessor", Namespaces.TRANSPORTATION), property, target);
            }
        }

        if (source.isSetSuccessors()) {
            for (TrafficSpaceReference property : source.getSuccessors()) {
                helper.addRelatedFeature(Name.of("successor", Namespaces.TRANSPORTATION), property, target);
            }
        }

        if (source.isSetClearanceSpaces()) {
            for (ClearanceSpaceProperty property : source.getClearanceSpaces()) {
                helper.addContainedFeature(Name.of("clearanceSpace", Namespaces.TRANSPORTATION), property, target);
            }
        }
    }

    @Override
    public TrafficSpace createObject(Feature source) throws ModelSerializeException {
        return new TrafficSpace();
    }

    @Override
    public void serialize(Feature source, TrafficSpace target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.TRANSPORTATION);

        source.getAttributes().getFirst(Name.of("granularity", Namespaces.TRANSPORTATION))
                .flatMap(Attribute::getStringValue)
                .ifPresent(value -> target.setGranularity(GranularityValue.fromValue(value)));

        source.getAttributes().getFirst(Name.of("trafficDirection", Namespaces.TRANSPORTATION))
                .flatMap(Attribute::getStringValue)
                .ifPresent(value -> target.setTrafficDirection(TrafficDirectionValue.fromValue(value)));

        for (Attribute attribute : source.getAttributes().get(Name.of("occupancy", Namespaces.TRANSPORTATION))) {
            target.getOccupancies().add(new OccupancyProperty(helper.getAttribute(attribute, OccupancyAdapter.class)));
        }

        for (FeatureProperty property : source.getFeatures().get(Name.of("predecessor", Namespaces.TRANSPORTATION))) {
            target.getPredecessors().add(helper.getObjectProperty(property, TrafficSpaceReferenceAdapter.class));
        }

        for (FeatureProperty property : source.getFeatures().get(Name.of("successor", Namespaces.TRANSPORTATION))) {
            target.getSuccessors().add(helper.getObjectProperty(property, TrafficSpaceReferenceAdapter.class));
        }

        for (FeatureProperty property : source.getFeatures().get(Name.of("clearanceSpace", Namespaces.TRANSPORTATION))) {
            target.getClearanceSpaces().add(helper.getObjectProperty(property, ClearanceSpacePropertyAdapter.class));
        }
    }
}
