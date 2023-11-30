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

import org.citydb.io.citygml.adapter.construction.AbstractConstructionAdapter;
import org.citydb.io.citygml.adapter.core.SpaceGeometrySupport;
import org.citydb.io.citygml.adapter.geometry.builder.Lod;
import org.citydb.io.citygml.adapter.geometry.serializer.MultiCurvePropertyAdapter;
import org.citydb.io.citygml.adapter.geometry.serializer.MultiSurfacePropertyAdapter;
import org.citydb.io.citygml.adapter.geometry.serializer.SolidPropertyAdapter;
import org.citydb.io.citygml.adapter.gml.CodeAdapter;
import org.citydb.io.citygml.adapter.gml.MeasureOrNilReasonListAdapter;
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
import org.citygml4j.core.model.building.*;
import org.citygml4j.core.model.construction.RoofSurface;
import org.citygml4j.core.model.core.AbstractSpaceBoundaryProperty;
import org.citygml4j.core.model.core.AddressProperty;
import org.citygml4j.core.model.deprecated.building.DeprecatedPropertiesOfAbstractBuilding;

public abstract class AbstractBuildingAdapter<T extends AbstractBuilding> extends AbstractConstructionAdapter<T> {

    @Override
    public void build(T source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.BUILDING);

        if (source.getRoofType() != null) {
            helper.addAttribute(Name.of("roofType", Namespaces.BUILDING), source.getRoofType(), target,
                    CodeAdapter.class);
        }

        if (source.getStoreysAboveGround() != null) {
            target.addAttribute(Attribute.of(Name.of("storeysAboveGround", Namespaces.BUILDING), DataType.INTEGER)
                    .setIntValue(source.getStoreysAboveGround()));
        }

        if (source.getStoreysBelowGround() != null) {
            target.addAttribute(Attribute.of(Name.of("storeysBelowGround", Namespaces.BUILDING), DataType.INTEGER)
                    .setIntValue(source.getStoreysBelowGround()));
        }

        if (source.getStoreyHeightsAboveGround() != null) {
            helper.addAttribute(Name.of("storeyHeightsAboveGround", Namespaces.BUILDING),
                    source.getStoreyHeightsAboveGround(), target, MeasureOrNilReasonListAdapter.class);
        }

        if (source.getStoreyHeightsBelowGround() != null) {
            helper.addAttribute(Name.of("storeyHeightsBelowGround", Namespaces.BUILDING),
                    source.getStoreyHeightsBelowGround(), target, MeasureOrNilReasonListAdapter.class);
        }

        if (source.isSetBuildingConstructiveElements()) {
            for (BuildingConstructiveElementProperty property : source.getBuildingConstructiveElements()) {
                helper.addFeature(Name.of("buildingConstructiveElement", Namespaces.BUILDING), property,
                        target);
            }
        }

        if (source.isSetBuildingInstallations()) {
            for (BuildingInstallationProperty property : source.getBuildingInstallations()) {
                helper.addFeature(Name.of("buildingInstallation", Namespaces.BUILDING), property, target);
            }
        }

        if (source.isSetBuildingRooms()) {
            for (BuildingRoomProperty property : source.getBuildingRooms()) {
                helper.addFeature(Name.of("buildingRoom", Namespaces.BUILDING), property, target);
            }
        }

        if (source.isSetBuildingFurniture()) {
            for (BuildingFurnitureProperty property : source.getBuildingFurniture()) {
                helper.addFeature(Name.of("buildingFurniture", Namespaces.BUILDING), property, target);
            }
        }

        if (source.isSetBuildingSubdivisions()) {
            for (AbstractBuildingSubdivisionProperty property : source.getBuildingSubdivisions()) {
                helper.addFeature(Name.of("buildingSubdivision", Namespaces.BUILDING), property, target);
            }
        }

        if (source.isSetAddresses()) {
            for (AddressProperty property : source.getAddresses()) {
                helper.addAddress(Name.of("address", Namespaces.BUILDING), property, target);
            }
        }

        if (source.hasDeprecatedProperties()) {
            DeprecatedPropertiesOfAbstractBuilding properties = source.getDeprecatedProperties();
            if (properties.getLod0RoofEdge() != null) {
                helper.addSurfaceGeometry(Name.of("lod0RoofEdge", Namespaces.DEPRECATED),
                        properties.getLod0RoofEdge(), Lod.of(0), target);
            }

            if (properties.getLod1MultiSurface() != null) {
                helper.addSurfaceGeometry(Name.of("lod1MultiSurface", Namespaces.DEPRECATED),
                        properties.getLod1MultiSurface(), Lod.of(1), target);
            }

            if (properties.getLod4MultiCurve() != null) {
                helper.addCurveGeometry(Name.of("lod4MultiCurve", Namespaces.DEPRECATED),
                        properties.getLod4MultiCurve(), Lod.of(4), target);
            }

            if (properties.getLod4MultiSurface() != null) {
                helper.addSurfaceGeometry(Name.of("lod4MultiSurface", Namespaces.DEPRECATED),
                        properties.getLod4MultiSurface(), Lod.of(4), target);
            }

            if (properties.getLod4Solid() != null) {
                helper.addSolidGeometry(Name.of("lod4Solid", Namespaces.DEPRECATED),
                        properties.getLod4Solid(), Lod.of(4), target);
            }

            if (properties.getLod4TerrainIntersectionCurve() != null) {
                helper.addCurveGeometry(Name.of("lod4TerrainIntersectionCurve", Namespaces.DEPRECATED),
                        properties.getLod4TerrainIntersectionCurve(), Lod.of(4), target);
            }
        }
    }

    @Override
    public void serialize(Feature source, T target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.BUILDING);

        Attribute roofType = source.getAttributes()
                .getFirst(Name.of("roofType", Namespaces.BUILDING))
                .orElse(null);
        if (roofType != null) {
            target.setRoofType(helper.getAttribute(roofType, CodeAdapter.class));
        }

        source.getAttributes().getFirst(Name.of("storeysAboveGround", Namespaces.BUILDING))
                .flatMap(Attribute::getIntValue)
                .ifPresent(value -> target.setStoreysAboveGround(value.intValue()));

        source.getAttributes().getFirst(Name.of("storeysBelowGround", Namespaces.BUILDING))
                .flatMap(Attribute::getIntValue)
                .ifPresent(value -> target.setStoreysBelowGround(value.intValue()));

        Attribute storeyHeightsAboveGround = source.getAttributes()
                .getFirst(Name.of("storeyHeightsAboveGround", Namespaces.BUILDING))
                .orElse(null);
        if (storeyHeightsAboveGround != null) {
            target.setStoreyHeightsAboveGround(
                    helper.getAttribute(storeyHeightsAboveGround, MeasureOrNilReasonListAdapter.class));
        }

        Attribute storeyHeightsBelowGround = source.getAttributes()
                .getFirst(Name.of("storeyHeightsBelowGround", Namespaces.BUILDING))
                .orElse(null);
        if (storeyHeightsBelowGround != null) {
            target.setStoreyHeightsBelowGround(
                    helper.getAttribute(storeyHeightsBelowGround, MeasureOrNilReasonListAdapter.class));
        }

        if (helper.isMapLod0RoofEdge()) {
            GeometryProperty lod0RoofEdge = source.getGeometries()
                    .getFirst(Name.of("lod0RoofEdge", Namespaces.DEPRECATED))
                    .orElse(null);
            if (lod0RoofEdge != null) {
                RoofSurface roofSurface = new RoofSurface();
                roofSurface.setLod0MultiSurface(helper.getGeometryProperty(lod0RoofEdge,
                        MultiSurfacePropertyAdapter.class));
                target.addBoundary(new AbstractSpaceBoundaryProperty(roofSurface));
            }
        }

        for (FeatureProperty property : source.getFeatures().get(Name.of("buildingConstructiveElement", Namespaces.BUILDING))) {
            target.getBuildingConstructiveElements().add(
                    helper.getObjectProperty(property, BuildingConstructiveElementPropertyAdapter.class));
        }

        for (FeatureProperty property : source.getFeatures().get(Name.of("buildingInstallation", Namespaces.BUILDING))) {
            target.getBuildingInstallations().add(
                    helper.getObjectProperty(property, BuildingInstallationPropertyAdapter.class));
        }

        for (FeatureProperty property : source.getFeatures().get(Name.of("buildingRoom", Namespaces.BUILDING))) {
            target.getBuildingRooms().add(helper.getObjectProperty(property, BuildingRoomPropertyAdapter.class));
        }

        for (FeatureProperty property : source.getFeatures().get(Name.of("buildingFurniture", Namespaces.BUILDING))) {
            target.getBuildingFurniture().add(
                    helper.getObjectProperty(property, BuildingFurniturePropertyAdapter.class));
        }

        for (FeatureProperty property : source.getFeatures().get(Name.of("buildingSubdivision", Namespaces.BUILDING))) {
            target.getBuildingSubdivisions().add(
                    helper.getObjectProperty(property, AbstractBuildingSubdivisionPropertyAdapter.class));
        }

        for (org.citydb.model.property.AddressProperty property : source.getAddresses().getAll()) {
            target.getAddresses().add(helper.getAddressProperty(property));
        }
    }

    @Override
    public void postSerialize(Feature source, T target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.postSerialize(source, target, helper);

        if (helper.getCityGMLVersion() == CityGMLVersion.v2_0
                && source.getGeometries().containsNamespace(Namespaces.DEPRECATED)) {
            GeometryProperty lod0RoofEdge = source.getGeometries()
                    .getFirst(Name.of("lod0RoofEdge", Namespaces.DEPRECATED))
                    .orElse(null);
            if (lod0RoofEdge != null) {
                target.getDeprecatedProperties().setLod0RoofEdge(
                        helper.getGeometryProperty(lod0RoofEdge, MultiSurfacePropertyAdapter.class));
            }

            GeometryProperty lod1MultiSurface = source.getGeometries()
                    .getFirst(Name.of("lod1MultiSurface", Namespaces.DEPRECATED))
                    .orElse(null);
            if (lod1MultiSurface != null) {
                target.getDeprecatedProperties().setLod1MultiSurface(
                        helper.getGeometryProperty(lod1MultiSurface, MultiSurfacePropertyAdapter.class));
            }

            GeometryProperty lod4MultiCurve = source.getGeometries()
                    .getFirst(Name.of("lod4MultiCurve", Namespaces.DEPRECATED))
                    .orElse(null);
            if (lod4MultiCurve != null) {
                target.getDeprecatedProperties().setLod4MultiCurve(
                        helper.getGeometryProperty(lod4MultiCurve, MultiCurvePropertyAdapter.class));
            }

            GeometryProperty lod4MultiSurface = source.getGeometries()
                    .getFirst(Name.of("lod4MultiSurface", Namespaces.DEPRECATED))
                    .orElse(null);
            if (lod4MultiSurface != null) {
                target.getDeprecatedProperties().setLod4MultiSurface(
                        helper.getGeometryProperty(lod4MultiSurface, MultiSurfacePropertyAdapter.class));
            }

            GeometryProperty lod4Solid = source.getGeometries()
                    .getFirst(Name.of("lod4Solid", Namespaces.DEPRECATED))
                    .orElse(null);
            if (lod4Solid != null) {
                target.getDeprecatedProperties().setLod4Solid(
                        helper.getGeometryProperty(lod4Solid, SolidPropertyAdapter.class));
            }

            GeometryProperty lod4TerrainIntersectionCurve = source.getGeometries()
                    .getFirst(Name.of("lod4TerrainIntersectionCurve", Namespaces.DEPRECATED))
                    .orElse(null);
            if (lod4TerrainIntersectionCurve != null) {
                target.getDeprecatedProperties().setLod4TerrainIntersectionCurve(
                        helper.getGeometryProperty(lod4TerrainIntersectionCurve, MultiCurvePropertyAdapter.class));
            }
        }
    }

    @Override
    protected void configureSerializer(SpaceGeometrySupport<T> geometrySupport) {
        geometrySupport.withLod0MultiSurface()
                .withLod1Solid()
                .withLod2Solid()
                .withLod2MultiSurface()
                .withLod2MultiCurve()
                .withLod3Solid()
                .withLod3MultiSurface()
                .withLod3MultiCurve()
                .withLod1TerrainIntersectionCurve()
                .withLod2TerrainIntersectionCurve()
                .withLod3TerrainIntersectionCurve();
    }
}
