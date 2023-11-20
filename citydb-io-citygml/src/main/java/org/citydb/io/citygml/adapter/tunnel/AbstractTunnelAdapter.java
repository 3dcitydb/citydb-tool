/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
 * Virtual City Systems, Germany
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

import org.citydb.io.citygml.adapter.construction.AbstractConstructionAdapter;
import org.citydb.io.citygml.adapter.core.SpaceGeometrySupport;
import org.citydb.io.citygml.adapter.geometry.builder.Lod;
import org.citydb.io.citygml.adapter.geometry.serializer.MultiCurvePropertyAdapter;
import org.citydb.io.citygml.adapter.geometry.serializer.MultiSurfacePropertyAdapter;
import org.citydb.io.citygml.adapter.geometry.serializer.SolidPropertyAdapter;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.property.FeatureProperty;
import org.citydb.model.property.GeometryProperty;
import org.citygml4j.core.model.CityGMLVersion;
import org.citygml4j.core.model.deprecated.tunnel.DeprecatedPropertiesOfAbstractTunnel;
import org.citygml4j.core.model.tunnel.*;

import java.util.function.BiFunction;

public abstract class AbstractTunnelAdapter<T extends AbstractTunnel> extends AbstractConstructionAdapter<T> {

    @Override
    public void build(T source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.TUNNEL);

        if (source.isSetTunnelConstructiveElements()) {
            for (TunnelConstructiveElementProperty property : source.getTunnelConstructiveElements()) {
                helper.addFeature(Name.of("tunnelConstructiveElement", Namespaces.TUNNEL), property,
                        target);
            }
        }

        if (source.isSetTunnelInstallations()) {
            for (TunnelInstallationProperty property : source.getTunnelInstallations()) {
                helper.addFeature(Name.of("tunnelInstallation", Namespaces.TUNNEL), property, target);
            }
        }

        if (source.isSetHollowSpaces()) {
            for (HollowSpaceProperty property : source.getHollowSpaces()) {
                helper.addFeature(Name.of("hollowSpace", Namespaces.TUNNEL), property, target);
            }
        }

        if (source.isSetTunnelFurniture()) {
            for (TunnelFurnitureProperty property : source.getTunnelFurniture()) {
                helper.addFeature(Name.of("tunnelFurniture", Namespaces.TUNNEL), property, target);
            }
        }

        if (source.hasDeprecatedProperties()) {
            DeprecatedPropertiesOfAbstractTunnel properties = source.getDeprecatedProperties();
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
        helper.addStandardObjectClassifiers(source, target, Namespaces.TUNNEL);

        for (FeatureProperty property : source.getFeatures().get(Name.of("tunnelConstructiveElement", Namespaces.TUNNEL))) {
            target.getTunnelConstructiveElements().add(
                    helper.getObjectProperty(property, TunnelConstructiveElementPropertyAdapter.class));
        }

        for (FeatureProperty property : source.getFeatures().get(Name.of("tunnelInstallation", Namespaces.TUNNEL))) {
            target.getTunnelInstallations().add(
                    helper.getObjectProperty(property, TunnelInstallationPropertyAdapter.class));
        }

        for (FeatureProperty property : source.getFeatures().get(Name.of("hollowSpace", Namespaces.TUNNEL))) {
            target.getHollowSpaces().add(helper.getObjectProperty(property, HollowSpacePropertyAdapter.class));
        }

        for (FeatureProperty property : source.getFeatures().get(Name.of("tunnelFurniture", Namespaces.TUNNEL))) {
            target.getTunnelFurniture().add(helper.getObjectProperty(property, TunnelFurniturePropertyAdapter.class));
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
        BiFunction<CityGMLVersion, T, Boolean> predicate = (version, target) -> version == CityGMLVersion.v2_0;
        geometrySupport.withLod1Solid(predicate)
                .withLod2Solid(predicate)
                .withLod2MultiSurface(predicate)
                .withLod2MultiCurve(predicate)
                .withLod3Solid(predicate)
                .withLod3MultiSurface(predicate)
                .withLod3MultiCurve(predicate)
                .withLod1TerrainIntersectionCurve(predicate)
                .withLod2TerrainIntersectionCurve(predicate)
                .withLod3TerrainIntersectionCurve(predicate);
    }
}
