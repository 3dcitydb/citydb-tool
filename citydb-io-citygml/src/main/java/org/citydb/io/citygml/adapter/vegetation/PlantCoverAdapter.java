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

package org.citydb.io.citygml.adapter.vegetation;

import org.citydb.io.citygml.adapter.core.SpaceGeometrySupport;
import org.citydb.io.citygml.adapter.geometry.builder.Lod;
import org.citydb.io.citygml.adapter.geometry.serializer.MultiSolidPropertyAdapter;
import org.citydb.io.citygml.adapter.geometry.serializer.MultiSurfacePropertyAdapter;
import org.citydb.io.citygml.adapter.gml.LengthAdapter;
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
import org.citydb.model.property.GeometryProperty;
import org.citygml4j.core.model.CityGMLVersion;
import org.citygml4j.core.model.deprecated.vegetation.DeprecatedPropertiesOfPlantCover;
import org.citygml4j.core.model.vegetation.PlantCover;

@DatabaseType(name = "PlantCover", namespace = Namespaces.VEGETATION)
public class PlantCoverAdapter extends AbstractVegetationObjectAdapter<PlantCover> {

    @Override
    public Feature createModel(PlantCover source) throws ModelBuildException {
        return Feature.of(FeatureType.PLANT_COVER);
    }

    @Override
    public void build(PlantCover source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.VEGETATION);

        if (source.getAverageHeight() != null) {
            helper.addAttribute(Name.of("averageHeight", Namespaces.VEGETATION), source.getAverageHeight(), target,
                    LengthAdapter.class);
        }

        if (source.getMinHeight() != null) {
            helper.addAttribute(Name.of("minHeight", Namespaces.VEGETATION), source.getMinHeight(), target,
                    LengthAdapter.class);
        }

        if (source.getMaxHeight() != null) {
            helper.addAttribute(Name.of("maxHeight", Namespaces.VEGETATION), source.getMaxHeight(), target,
                    LengthAdapter.class);
        }

        if (source.hasDeprecatedProperties()) {
            DeprecatedPropertiesOfPlantCover properties = source.getDeprecatedProperties();
            if (properties.getLod1MultiSurface() != null) {
                helper.addSurfaceGeometry(Name.of("lod1MultiSurface", Namespaces.DEPRECATED),
                        properties.getLod1MultiSurface(), Lod.of(1), target);
            }

            if (properties.getLod4MultiSurface() != null) {
                helper.addSurfaceGeometry(Name.of("lod4MultiSurface", Namespaces.DEPRECATED),
                        properties.getLod4MultiSurface(), Lod.of(4), target);
            }

            if (properties.getLod1MultiSolid() != null) {
                helper.addSolidGeometry(Name.of("lod1MultiSolid", Namespaces.DEPRECATED),
                        properties.getLod1MultiSolid(), Lod.of(1), target);
            }

            if (properties.getLod2MultiSolid() != null) {
                helper.addSolidGeometry(Name.of("lod2MultiSolid", Namespaces.DEPRECATED),
                        properties.getLod2MultiSolid(), Lod.of(2), target);
            }

            if (properties.getLod3MultiSolid() != null) {
                helper.addSolidGeometry(Name.of("lod3MultiSolid", Namespaces.DEPRECATED),
                        properties.getLod3MultiSolid(), Lod.of(3), target);
            }

            if (properties.getLod4MultiSolid() != null) {
                helper.addSolidGeometry(Name.of("lod4MultiSolid", Namespaces.DEPRECATED),
                        properties.getLod4MultiSolid(), Lod.of(4), target);
            }
        }
    }

    @Override
    public PlantCover createObject(Feature source) throws ModelSerializeException {
        return new PlantCover();
    }

    @Override
    public void serialize(Feature source, PlantCover target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.VEGETATION);

        Attribute averageHeight = source.getAttributes()
                .getFirst(Name.of("averageHeight", Namespaces.VEGETATION))
                .orElse(null);
        if (averageHeight != null) {
            target.setAverageHeight(helper.getAttribute(averageHeight, LengthAdapter.class));
        }

        Attribute minHeight = source.getAttributes().getFirst(Name.of("minHeight", Namespaces.VEGETATION)).orElse(null);
        if (minHeight != null) {
            target.setMinHeight(helper.getAttribute(minHeight, LengthAdapter.class));
        }

        Attribute maxHeight = source.getAttributes().getFirst(Name.of("maxHeight", Namespaces.VEGETATION)).orElse(null);
        if (maxHeight != null) {
            target.setMaxHeight(helper.getAttribute(maxHeight, LengthAdapter.class));
        }
    }

    @Override
    public void postSerialize(Feature source, PlantCover target, ModelSerializerHelper helper) throws ModelSerializeException {
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

            GeometryProperty lod1Solid = source.getGeometries()
                    .getFirst(Name.of("lod1MultiSolid", Namespaces.DEPRECATED))
                    .orElse(null);
            if (lod1Solid != null) {
                target.getDeprecatedProperties().setLod1MultiSolid(
                        helper.getGeometryProperty(lod1Solid, MultiSolidPropertyAdapter.class));
            }

            GeometryProperty lod2Solid = source.getGeometries()
                    .getFirst(Name.of("lod2MultiSolid", Namespaces.DEPRECATED))
                    .orElse(null);
            if (lod2Solid != null) {
                target.getDeprecatedProperties().setLod2MultiSolid(
                        helper.getGeometryProperty(lod2Solid, MultiSolidPropertyAdapter.class));
            }

            GeometryProperty lod3Solid = source.getGeometries()
                    .getFirst(Name.of("lod3MultiSolid", Namespaces.DEPRECATED))
                    .orElse(null);
            if (lod3Solid != null) {
                target.getDeprecatedProperties().setLod3MultiSolid(
                        helper.getGeometryProperty(lod3Solid, MultiSolidPropertyAdapter.class));
            }

            GeometryProperty lod4Solid = source.getGeometries()
                    .getFirst(Name.of("lod4MultiSolid", Namespaces.DEPRECATED))
                    .orElse(null);
            if (lod4Solid != null) {
                target.getDeprecatedProperties().setLod4MultiSolid(
                        helper.getGeometryProperty(lod4Solid, MultiSolidPropertyAdapter.class));
            }
        }
    }

    @Override
    protected void configureSerializer(SpaceGeometrySupport<PlantCover> geometrySupport) {
        geometrySupport.withLod1Solid()
                .withLod2Solid()
                .withLod2MultiSurface()
                .withLod3Solid()
                .withLod3MultiSurface();
    }
}
