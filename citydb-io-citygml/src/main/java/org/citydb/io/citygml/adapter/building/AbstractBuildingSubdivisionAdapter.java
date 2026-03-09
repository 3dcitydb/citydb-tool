/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.building;

import org.citydb.io.citygml.adapter.construction.ElevationAdapter;
import org.citydb.io.citygml.adapter.core.AbstractLogicalSpaceAdapter;
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
import org.citygml4j.core.model.building.*;
import org.citygml4j.core.model.construction.ElevationProperty;

public abstract class AbstractBuildingSubdivisionAdapter<T extends AbstractBuildingSubdivision> extends AbstractLogicalSpaceAdapter<T> {

    @Override
    public void build(T source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.BUILDING);

        if (source.isSetElevations()) {
            for (ElevationProperty property : source.getElevations()) {
                if (property != null) {
                    helper.addAttribute(Name.of("elevation", Namespaces.BUILDING), property.getObject(), target,
                            ElevationAdapter.class);
                }
            }
        }

        if (source.getSortKey() != null) {
            target.addAttribute(Attribute.of(Name.of("sortKey", Namespaces.BUILDING), DataType.DOUBLE)
                    .setDoubleValue(source.getSortKey()));
        }

        if (source.isSetBuildingConstructiveElements()) {
            for (BuildingConstructiveElementProperty property : source.getBuildingConstructiveElements()) {
                helper.addContainedFeature(Name.of("buildingConstructiveElement", Namespaces.BUILDING), property,
                        target);
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

        if (source.isSetBuildingRooms()) {
            for (BuildingRoomProperty property : source.getBuildingRooms()) {
                helper.addContainedFeature(Name.of("buildingRoom", Namespaces.BUILDING), property, target);
            }
        }
    }

    @Override
    public void serialize(Feature source, T target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.BUILDING);

        for (Attribute attribute : source.getAttributes().get(Name.of("elevation", Namespaces.BUILDING))) {
            target.getElevations().add(new ElevationProperty(helper.getAttribute(attribute, ElevationAdapter.class)));
        }

        source.getAttributes().getFirst(Name.of("sortKey", Namespaces.BUILDING))
                .flatMap(Attribute::getDoubleValue)
                .ifPresent(target::setSortKey);

        for (FeatureProperty property : source.getFeatures().get(Name.of("buildingConstructiveElement", Namespaces.BUILDING))) {
            target.getBuildingConstructiveElements().add(
                    helper.getObjectProperty(property, BuildingConstructiveElementPropertyAdapter.class));
        }

        for (FeatureProperty property : source.getFeatures().get(Name.of("buildingFurniture", Namespaces.BUILDING))) {
            target.getBuildingFurniture().add(
                    helper.getObjectProperty(property, BuildingFurniturePropertyAdapter.class));
        }

        for (FeatureProperty property : source.getFeatures().get(Name.of("buildingInstallation", Namespaces.BUILDING))) {
            target.getBuildingInstallations().add(
                    helper.getObjectProperty(property, BuildingInstallationPropertyAdapter.class));
        }

        for (FeatureProperty property : source.getFeatures().get(Name.of("buildingRoom", Namespaces.BUILDING))) {
            target.getBuildingRooms().add(helper.getObjectProperty(property, BuildingRoomPropertyAdapter.class));
        }
    }
}
