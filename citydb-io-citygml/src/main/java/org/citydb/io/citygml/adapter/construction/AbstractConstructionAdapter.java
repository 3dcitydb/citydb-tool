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

package org.citydb.io.citygml.adapter.construction;

import org.citydb.io.citygml.adapter.core.AbstractOccupiedSpaceAdapter;
import org.citydb.io.citygml.adapter.core.OccupancyAdapter;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.property.Attribute;
import org.citydb.model.property.DataType;
import org.citygml4j.core.model.construction.*;
import org.citygml4j.core.model.core.OccupancyProperty;

import java.time.ZoneOffset;

public abstract class AbstractConstructionAdapter<T extends AbstractConstruction> extends AbstractOccupiedSpaceAdapter<T> {

    @Override
    public void build(T source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);

        if (source.getConditionOfConstruction() != null) {
            target.addAttribute(Attribute.of(Name.of("conditionOfConstruction", Namespaces.CONSTRUCTION), DataType.STRING)
                    .setStringValue(source.getConditionOfConstruction().toValue()));
        }

        if (source.getDateOfConstruction() != null) {
            target.addAttribute(Attribute.of(Name.of("dateOfConstruction", Namespaces.CONSTRUCTION), DataType.TIMESTAMP)
                    .setTimeStamp(source.getDateOfConstruction().atStartOfDay().atOffset(ZoneOffset.UTC)));
        }

        if (source.getDateOfDemolition() != null) {
            target.addAttribute(Attribute.of(Name.of("dateOfDemolition", Namespaces.CONSTRUCTION), DataType.TIMESTAMP)
                    .setTimeStamp(source.getDateOfDemolition().atStartOfDay().atOffset(ZoneOffset.UTC)));
        }

        if (source.isSetConstructionEvents()) {
            for (ConstructionEventProperty property : source.getConstructionEvents()) {
                if (property != null) {
                    helper.addAttribute(Name.of("constructionEvent", Namespaces.CONSTRUCTION), property.getObject(),
                            target, ConstructionEventAdapter.class);
                }
            }
        }

        if (source.isSetElevations()) {
            for (ElevationProperty property : source.getElevations()) {
                if (property != null) {
                    helper.addAttribute(Name.of("elevation", Namespaces.CONSTRUCTION), property.getObject(), target,
                            ElevationAdapter.class);
                }
            }
        }

        if (source.isSetHeights()) {
            for (HeightProperty property : source.getHeights()) {
                if (property != null) {
                    helper.addAttribute(Name.of("height", Namespaces.CONSTRUCTION), property.getObject(), target,
                            HeightAdapter.class);
                }
            }
        }

        if (source.isSetOccupancies()) {
            for (OccupancyProperty property : source.getOccupancies()) {
                if (property != null) {
                    helper.addAttribute(Name.of("occupancy", Namespaces.CONSTRUCTION), property.getObject(), target,
                            OccupancyAdapter.class);
                }
            }
        }
    }

    @Override
    public void serialize(Feature source, T target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);

        source.getAttributes().getFirst(Name.of("conditionOfConstruction", Namespaces.CONSTRUCTION))
                .flatMap(Attribute::getStringValue)
                .ifPresent(value -> target.setConditionOfConstruction(ConditionOfConstructionValue.fromValue(value)));

        source.getAttributes().getFirst(Name.of("dateOfConstruction", Namespaces.CONSTRUCTION))
                .flatMap(Attribute::getTimeStamp)
                .ifPresent(value -> target.setDateOfConstruction(value.toLocalDate()));

        source.getAttributes().getFirst(Name.of("dateOfDemolition", Namespaces.CONSTRUCTION))
                .flatMap(Attribute::getTimeStamp)
                .ifPresent(value -> target.setDateOfDemolition(value.toLocalDate()));

        for (Attribute attribute : source.getAttributes().get(Name.of("constructionEvent", Namespaces.CONSTRUCTION))) {
            target.getConstructionEvents().add(new ConstructionEventProperty(
                    helper.getAttribute(attribute, ConstructionEventAdapter.class)));
        }

        for (Attribute attribute : source.getAttributes().get(Name.of("elevation", Namespaces.CONSTRUCTION))) {
            target.getElevations().add(new ElevationProperty(helper.getAttribute(attribute, ElevationAdapter.class)));
        }

        for (Attribute attribute : source.getAttributes().get(Name.of("height", Namespaces.CONSTRUCTION))) {
            target.getHeights().add(new HeightProperty(helper.getAttribute(attribute, HeightAdapter.class)));
        }

        for (Attribute attribute : source.getAttributes().get(Name.of("occupancy", Namespaces.CONSTRUCTION))) {
            target.getOccupancies().add(new OccupancyProperty(helper.getAttribute(attribute, OccupancyAdapter.class)));
        }
    }
}
