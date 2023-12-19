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

package org.citydb.io.citygml.adapter.transportation;

import org.citydb.io.citygml.adapter.core.AbstractUnoccupiedSpaceAdapter;
import org.citydb.io.citygml.adapter.core.OccupancyAdapter;
import org.citydb.io.citygml.adapter.core.SpaceGeometrySupport;
import org.citydb.io.citygml.adapter.geometry.builder.Lod;
import org.citydb.io.citygml.adapter.geometry.serializer.MultiSurfacePropertyAdapter;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.property.Attribute;
import org.citydb.model.property.DataType;
import org.citydb.model.property.FeatureProperty;
import org.citydb.model.property.GeometryProperty;
import org.citygml4j.core.model.CityGMLVersion;
import org.citygml4j.core.model.core.OccupancyProperty;
import org.citygml4j.core.model.deprecated.transportation.DeprecatedPropertiesOfAbstractTransportationSpace;
import org.citygml4j.core.model.transportation.*;

public abstract class AbstractTransportationSpaceAdapter<T extends AbstractTransportationSpace> extends AbstractUnoccupiedSpaceAdapter<T> {

    @Override
    public void build(T source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);

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

        if (source.isSetTrafficSpaces()) {
            for (TrafficSpaceProperty property : source.getTrafficSpaces()) {
                helper.addContainedFeature(Name.of("trafficSpace", Namespaces.TRANSPORTATION), property, target);
            }
        }

        if (source.isSetAuxiliaryTrafficSpaces()) {
            for (AuxiliaryTrafficSpaceProperty property : source.getAuxiliaryTrafficSpaces()) {
                helper.addContainedFeature(Name.of("auxiliaryTrafficSpace", Namespaces.TRANSPORTATION), property, target);
            }
        }

        if (source.isSetHoles()) {
            for (HoleProperty property : source.getHoles()) {
                helper.addContainedFeature(Name.of("hole", Namespaces.TRANSPORTATION), property, target);
            }
        }

        if (source.isSetMarkings()) {
            for (MarkingProperty property : source.getMarkings()) {
                helper.addContainedFeature(Name.of("marking", Namespaces.TRANSPORTATION), property, target);
            }
        }

        if (source.hasDeprecatedProperties()) {
            DeprecatedPropertiesOfAbstractTransportationSpace properties = source.getDeprecatedProperties();
            if (properties.getLod1MultiSurface() != null) {
                helper.addSurfaceGeometry(Name.of("lod1MultiSurface", Namespaces.DEPRECATED),
                        properties.getLod1MultiSurface(), Lod.of(1), target);
            }

            if (properties.getLod4MultiSurface() != null) {
                helper.addSurfaceGeometry(Name.of("lod4MultiSurface", Namespaces.DEPRECATED),
                        properties.getLod4MultiSurface(), Lod.of(4), target);
            }
        }
    }

    @Override
    public void serialize(Feature source, T target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);

        source.getAttributes().getFirst(Name.of("trafficDirection", Namespaces.TRANSPORTATION))
                .flatMap(Attribute::getStringValue)
                .ifPresent(value -> target.setTrafficDirection(TrafficDirectionValue.fromValue(value)));

        for (Attribute attribute : source.getAttributes().get(Name.of("occupancy", Namespaces.TRANSPORTATION))) {
            target.getOccupancies().add(new OccupancyProperty(helper.getAttribute(attribute, OccupancyAdapter.class)));
        }

        for (FeatureProperty property : source.getFeatures().get(Name.of("trafficSpace", Namespaces.TRANSPORTATION))) {
            target.getTrafficSpaces().add(helper.getObjectProperty(property, TrafficSpacePropertyAdapter.class));
        }

        for (FeatureProperty property : source.getFeatures().get(Name.of("auxiliaryTrafficSpace", Namespaces.TRANSPORTATION))) {
            target.getAuxiliaryTrafficSpaces().add(
                    helper.getObjectProperty(property, AuxiliaryTrafficSpacePropertyAdapter.class));
        }

        for (FeatureProperty property : source.getFeatures().get(Name.of("hole", Namespaces.TRANSPORTATION))) {
            target.getHoles().add(helper.getObjectProperty(property, HolePropertyAdapter.class));
        }

        for (FeatureProperty property : source.getFeatures().get(Name.of("marking", Namespaces.TRANSPORTATION))) {
            target.getMarkings().add(helper.getObjectProperty(property, MarkingPropertyAdapter.class));
        }
    }

    @Override
    public void postSerialize(Feature source, T target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.postSerialize(source, target, helper);

        if (helper.getCityGMLVersion() == CityGMLVersion.v2_0
                && source.getGeometries().containsNamespace(Namespaces.DEPRECATED)) {
            GeometryProperty lod1MultiSurface = source.getGeometries()
                    .getFirst(Name.of("lod1MultiSurface", Namespaces.DEPRECATED))
                    .orElse(null);
            if (lod1MultiSurface != null) {
                target.getDeprecatedProperties().setLod1MultiSurface(
                        helper.getGeometryProperty(lod1MultiSurface, MultiSurfacePropertyAdapter.class));
            }

            GeometryProperty lod4MultiSurface = source.getGeometries()
                    .getFirst(Name.of("lod4MultiSurface", Namespaces.DEPRECATED))
                    .orElse(null);
            if (lod4MultiSurface != null) {
                target.getDeprecatedProperties().setLod4MultiSurface(
                        helper.getGeometryProperty(lod4MultiSurface, MultiSurfacePropertyAdapter.class));
            }
        }
    }

    @Override
    protected void configureSerializer(SpaceGeometrySupport<T> geometrySupport) {
        geometrySupport.withLod0MultiCurve()
                .withLod2MultiSurface()
                .withLod3MultiSurface();
    }
}
