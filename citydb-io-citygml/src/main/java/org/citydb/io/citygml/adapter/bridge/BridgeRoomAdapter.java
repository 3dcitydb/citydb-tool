/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.bridge;

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
import org.citydb.model.property.FeatureProperty;
import org.citydb.model.property.GeometryProperty;
import org.citygml4j.core.model.CityGMLVersion;
import org.citygml4j.core.model.bridge.BridgeFurnitureProperty;
import org.citygml4j.core.model.bridge.BridgeInstallationProperty;
import org.citygml4j.core.model.bridge.BridgeRoom;
import org.citygml4j.core.model.deprecated.bridge.DeprecatedPropertiesOfBridgeRoom;

@DatabaseType(name = "BridgeRoom", namespace = Namespaces.BRIDGE)
public class BridgeRoomAdapter extends AbstractUnoccupiedSpaceAdapter<BridgeRoom> {

    @Override
    public Feature createModel(BridgeRoom source) throws ModelBuildException {
        return Feature.of(FeatureType.BRIDGE_ROOM);
    }

    @Override
    public void build(BridgeRoom source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.BRIDGE);

        if (source.isSetBridgeFurniture()) {
            for (BridgeFurnitureProperty property : source.getBridgeFurniture()) {
                helper.addContainedFeature(Name.of("bridgeFurniture", Namespaces.BRIDGE), property, target);
            }
        }

        if (source.isSetBridgeInstallations()) {
            for (BridgeInstallationProperty property : source.getBridgeInstallations()) {
                helper.addContainedFeature(Name.of("bridgeInstallation", Namespaces.BRIDGE), property, target);
            }
        }

        if (source.hasDeprecatedProperties()) {
            DeprecatedPropertiesOfBridgeRoom properties = source.getDeprecatedProperties();
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
    public BridgeRoom createObject(Feature source) throws ModelSerializeException {
        return new BridgeRoom();
    }

    @Override
    public void serialize(Feature source, BridgeRoom target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.BRIDGE);

        for (FeatureProperty property : source.getFeatures().get(Name.of("bridgeFurniture", Namespaces.BRIDGE))) {
            target.getBridgeFurniture().add(helper.getObjectProperty(property, BridgeFurniturePropertyAdapter.class));
        }

        for (FeatureProperty property : source.getFeatures().get(Name.of("bridgeInstallation", Namespaces.BRIDGE))) {
            target.getBridgeInstallations().add(
                    helper.getObjectProperty(property, BridgeInstallationPropertyAdapter.class));
        }
    }

    @Override
    public void postSerialize(Feature source, BridgeRoom target, ModelSerializerHelper helper) throws ModelSerializeException {
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
