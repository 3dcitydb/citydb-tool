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

package org.citydb.io.citygml.adapter.building;

import org.citydb.io.citygml.adapter.core.AbstractUnoccupiedSpaceAdapter;
import org.citydb.io.citygml.adapter.geometry.builder.Lod;
import org.citydb.io.citygml.adapter.geometry.serializer.MultiSurfacePropertyAdapter;
import org.citydb.io.citygml.adapter.geometry.serializer.SolidPropertyAdapter;
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
import org.citydb.model.property.FeatureProperty;
import org.citydb.model.property.GeometryProperty;
import org.citygml4j.core.model.CityGMLVersion;
import org.citygml4j.core.model.building.BuildingFurnitureProperty;
import org.citygml4j.core.model.building.BuildingInstallationProperty;
import org.citygml4j.core.model.building.BuildingRoom;
import org.citygml4j.core.model.building.RoomHeightProperty;
import org.citygml4j.core.model.deprecated.building.DeprecatedPropertiesOfBuildingRoom;

@DatabaseType(name = "BuildingRoom", namespace = Namespaces.BUILDING)
public class BuildingRoomAdapter extends AbstractUnoccupiedSpaceAdapter<BuildingRoom> {

    @Override
    public Feature createModel(BuildingRoom source) throws ModelBuildException {
        return Feature.of(FeatureType.BUILDING_ROOM);
    }

    @Override
    public void build(BuildingRoom source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.BUILDING);

        if (source.isSetRoomHeights()) {
            for (RoomHeightProperty property : source.getRoomHeights()) {
                if (property != null) {
                    helper.addAttribute(Name.of("roomHeight", Namespaces.BUILDING), property.getObject(), target,
                            RoomHeightAdapter.class);
                }
            }
        }

        if (source.isSetBuildingFurniture()) {
            for (BuildingFurnitureProperty property : source.getBuildingFurniture()) {
                helper.addContainedFeature(Name.of("buildingFurniture", Namespaces.BUILDING), property, target);
            }
        }

        if (source.isSetBuildingInstallations()) {
            for (BuildingInstallationProperty property : source.getBuildingInstallations()) {
                helper.addContainedFeature(Name.of("buildingInstallation", Namespaces.BUILDING), property, target);
            }
        }

        if (source.hasDeprecatedProperties()) {
            DeprecatedPropertiesOfBuildingRoom properties = source.getDeprecatedProperties();
            if (properties.getLod4Solid() != null) {
                helper.addSolidGeometry(Name.of("lod4Solid", Namespaces.DEPRECATED),
                        properties.getLod4Solid(), Lod.of(4), target);
            }

            if (properties.getLod4MultiSurface() != null) {
                helper.addSurfaceGeometry(Name.of("lod4MultiSurface", Namespaces.DEPRECATED),
                        properties.getLod4MultiSurface(), Lod.of(4), target);
            }
        }
    }

    @Override
    public BuildingRoom createObject(Feature source) throws ModelSerializeException {
        return new BuildingRoom();
    }

    @Override
    public void serialize(Feature source, BuildingRoom target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.BUILDING);

        for (Attribute attribute : source.getAttributes().get(Name.of("roomHeight", Namespaces.BUILDING))) {
            target.getRoomHeights().add(new RoomHeightProperty(
                    helper.getAttribute(attribute, RoomHeightAdapter.class)));
        }

        for (FeatureProperty property : source.getFeatures().get(Name.of("buildingFurniture", Namespaces.BUILDING))) {
            target.getBuildingFurniture().add(
                    helper.getObjectProperty(property, BuildingFurniturePropertyAdapter.class));
        }

        for (FeatureProperty property : source.getFeatures().get(Name.of("buildingInstallation", Namespaces.BUILDING))) {
            target.getBuildingInstallations().add(
                    helper.getObjectProperty(property, BuildingInstallationPropertyAdapter.class));
        }
    }

    @Override
    public void postSerialize(Feature source, BuildingRoom target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.postSerialize(source, target, helper);

        if (helper.getCityGMLVersion() == CityGMLVersion.v2_0
                && source.getGeometries().containsNamespace(Namespaces.DEPRECATED)) {
            GeometryProperty lod4Solid = source.getGeometries()
                    .getFirst(Name.of("lod4Solid", Namespaces.DEPRECATED))
                    .orElse(null);
            if (lod4Solid != null) {
                target.getDeprecatedProperties().setLod4Solid(
                        helper.getGeometryProperty(lod4Solid, SolidPropertyAdapter.class));
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
}
