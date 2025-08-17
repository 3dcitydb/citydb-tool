/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
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

package org.citydb.io.citygml.adapter.tunnel;

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
import org.citygml4j.core.model.deprecated.tunnel.DeprecatedPropertiesOfHollowSpace;
import org.citygml4j.core.model.tunnel.HollowSpace;
import org.citygml4j.core.model.tunnel.TunnelFurnitureProperty;
import org.citygml4j.core.model.tunnel.TunnelInstallationProperty;

@DatabaseType(name = "HollowSpace", namespace = Namespaces.TUNNEL)
public class HollowSpaceAdapter extends AbstractUnoccupiedSpaceAdapter<HollowSpace> {

    @Override
    public Feature createModel(HollowSpace source) throws ModelBuildException {
        return Feature.of(FeatureType.HOLLOW_SPACE);
    }

    @Override
    public void build(HollowSpace source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.TUNNEL);

        if (source.isSetTunnelFurniture()) {
            for (TunnelFurnitureProperty property : source.getTunnelFurniture()) {
                helper.addContainedFeature(Name.of("tunnelFurniture", Namespaces.TUNNEL), property, target);
            }
        }

        if (source.isSetTunnelInstallations()) {
            for (TunnelInstallationProperty property : source.getTunnelInstallations()) {
                helper.addContainedFeature(Name.of("tunnelInstallation", Namespaces.TUNNEL), property, target);
            }
        }

        if (source.hasDeprecatedProperties()) {
            DeprecatedPropertiesOfHollowSpace properties = source.getDeprecatedProperties();
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
    public HollowSpace createObject(Feature source) throws ModelSerializeException {
        return new HollowSpace();
    }

    @Override
    public void serialize(Feature source, HollowSpace target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.TUNNEL);

        for (FeatureProperty property : source.getFeatures().get(Name.of("tunnelFurniture", Namespaces.TUNNEL))) {
            target.getTunnelFurniture().add(helper.getObjectProperty(property, TunnelFurniturePropertyAdapter.class));
        }

        for (FeatureProperty property : source.getFeatures().get(Name.of("tunnelInstallation", Namespaces.TUNNEL))) {
            target.getTunnelInstallations().add(
                    helper.getObjectProperty(property, TunnelInstallationPropertyAdapter.class));
        }
    }

    @Override
    public void postSerialize(Feature source, HollowSpace target, ModelSerializerHelper helper) throws ModelSerializeException {
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
