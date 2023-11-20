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

package org.citydb.io.citygml.adapter.vegetation;

import org.citydb.io.citygml.adapter.core.ImplicitGeometryPropertyAdapter;
import org.citydb.io.citygml.adapter.core.SpaceGeometrySupport;
import org.citydb.io.citygml.adapter.geometry.builder.Lod;
import org.citydb.io.citygml.adapter.geometry.serializer.GeometryPropertyAdapter;
import org.citydb.io.citygml.adapter.gml.CodeAdapter;
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
import org.citydb.model.property.ImplicitGeometryProperty;
import org.citygml4j.core.model.CityGMLVersion;
import org.citygml4j.core.model.deprecated.vegetation.DeprecatedPropertiesOfSolitaryVegetationObject;
import org.citygml4j.core.model.vegetation.SolitaryVegetationObject;

@DatabaseType(name = "SolitaryVegetationObject", namespace = Namespaces.VEGETATION)
public class SolitaryVegetationObjectAdapter extends AbstractVegetationObjectAdapter<SolitaryVegetationObject> {

    @Override
    public Feature createModel(SolitaryVegetationObject source) throws ModelBuildException {
        return Feature.of(FeatureType.SOLITARY_VEGETATION_OBJECT);
    }

    @Override
    public void build(SolitaryVegetationObject source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.VEGETATION);

        if (source.getSpecies() != null) {
            helper.addAttribute(Name.of("species", Namespaces.VEGETATION), source.getSpecies(), target,
                    CodeAdapter.class);
        }

        if (source.getHeight() != null) {
            helper.addAttribute(Name.of("height", Namespaces.VEGETATION), source.getHeight(), target,
                    LengthAdapter.class);
        }

        if (source.getTrunkDiameter() != null) {
            helper.addAttribute(Name.of("trunkDiameter", Namespaces.VEGETATION), source.getTrunkDiameter(), target,
                    LengthAdapter.class);
        }

        if (source.getCrownDiameter() != null) {
            helper.addAttribute(Name.of("crownDiameter", Namespaces.VEGETATION), source.getCrownDiameter(), target,
                    LengthAdapter.class);
        }

        if (source.getRootBallDiameter() != null) {
            helper.addAttribute(Name.of("rootBallDiameter", Namespaces.VEGETATION), source.getRootBallDiameter(),
                    target, LengthAdapter.class);
        }

        if (source.getMaxRootBallDepth() != null) {
            helper.addAttribute(Name.of("maxRootBallDepth", Namespaces.VEGETATION), source.getMaxRootBallDepth(),
                    target, LengthAdapter.class);
        }

        if (source.hasDeprecatedProperties()) {
            DeprecatedPropertiesOfSolitaryVegetationObject properties = source.getDeprecatedProperties();
            if (properties.getLod1Geometry() != null) {
                helper.addGeometry(Name.of("lod1Geometry", Namespaces.DEPRECATED),
                        properties.getLod1Geometry(), Lod.of(1), target);
            }

            if (properties.getLod2Geometry() != null) {
                helper.addGeometry(Name.of("lod2Geometry", Namespaces.DEPRECATED),
                        properties.getLod2Geometry(), Lod.of(2), target);
            }

            if (properties.getLod3Geometry() != null) {
                helper.addGeometry(Name.of("lod3Geometry", Namespaces.DEPRECATED),
                        properties.getLod3Geometry(), Lod.of(3), target);
            }

            if (properties.getLod4Geometry() != null) {
                helper.addGeometry(Name.of("lod4Geometry", Namespaces.DEPRECATED),
                        properties.getLod4Geometry(), Lod.of(4), target);
            }

            if (properties.getLod4ImplicitRepresentation() != null) {
                helper.addImplicitGeometry(Name.of("lod4ImplicitRepresentation", Namespaces.DEPRECATED),
                        properties.getLod4ImplicitRepresentation(), Lod.of(4), target);
            }
        }
    }

    @Override
    public SolitaryVegetationObject createObject(Feature source) throws ModelSerializeException {
        return new SolitaryVegetationObject();
    }

    @Override
    public void serialize(Feature source, SolitaryVegetationObject target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.VEGETATION);

        Attribute species = source.getAttributes().getFirst(Name.of("species", Namespaces.VEGETATION)).orElse(null);
        if (species != null) {
            target.setSpecies(helper.getAttribute(species, CodeAdapter.class));
        }

        Attribute height = source.getAttributes().getFirst(Name.of("height", Namespaces.VEGETATION)).orElse(null);
        if (height != null) {
            target.setHeight(helper.getAttribute(height, LengthAdapter.class));
        }

        Attribute trunkDiameter = source.getAttributes()
                .getFirst(Name.of("trunkDiameter", Namespaces.VEGETATION))
                .orElse(null);
        if (trunkDiameter != null) {
            target.setTrunkDiameter(helper.getAttribute(trunkDiameter, LengthAdapter.class));
        }

        Attribute crownDiameter = source.getAttributes()
                .getFirst(Name.of("crownDiameter", Namespaces.VEGETATION))
                .orElse(null);
        if (crownDiameter != null) {
            target.setCrownDiameter(helper.getAttribute(crownDiameter, LengthAdapter.class));
        }

        Attribute rootBallDiameter = source.getAttributes()
                .getFirst(Name.of("rootBallDiameter", Namespaces.VEGETATION))
                .orElse(null);
        if (rootBallDiameter != null) {
            target.setRootBallDiameter(helper.getAttribute(rootBallDiameter, LengthAdapter.class));
        }

        Attribute maxRootBallDepth = source.getAttributes()
                .getFirst(Name.of("maxRootBallDepth", Namespaces.VEGETATION))
                .orElse(null);
        if (maxRootBallDepth != null) {
            target.setMaxRootBallDepth(helper.getAttribute(maxRootBallDepth, LengthAdapter.class));
        }
    }

    @Override
    public void postSerialize(Feature source, SolitaryVegetationObject target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.postSerialize(source, target, helper);

        if (helper.getCityGMLVersion() == CityGMLVersion.v2_0
                && source.getGeometries().containsNamespace(Namespaces.DEPRECATED)) {
            GeometryProperty lod1Geometry = source.getGeometries()
                    .getFirst(Name.of("lod1Geometry", Namespaces.DEPRECATED))
                    .orElse(null);
            if (lod1Geometry != null) {
                target.getDeprecatedProperties().setLod1Geometry(
                        helper.getGeometryProperty(lod1Geometry, GeometryPropertyAdapter.class));
            }

            GeometryProperty lod2Geometry = source.getGeometries()
                    .getFirst(Name.of("lod2Geometry", Namespaces.DEPRECATED))
                    .orElse(null);
            if (lod2Geometry != null) {
                target.getDeprecatedProperties().setLod2Geometry(
                        helper.getGeometryProperty(lod2Geometry, GeometryPropertyAdapter.class));
            }

            GeometryProperty lod3Geometry = source.getGeometries()
                    .getFirst(Name.of("lod3Geometry", Namespaces.DEPRECATED))
                    .orElse(null);
            if (lod3Geometry != null) {
                target.getDeprecatedProperties().setLod3Geometry(
                        helper.getGeometryProperty(lod3Geometry, GeometryPropertyAdapter.class));
            }

            GeometryProperty lod4Geometry = source.getGeometries()
                    .getFirst(Name.of("lod4Geometry", Namespaces.DEPRECATED))
                    .orElse(null);
            if (lod4Geometry != null) {
                target.getDeprecatedProperties().setLod4Geometry(
                        helper.getGeometryProperty(lod4Geometry, GeometryPropertyAdapter.class));
            }

            ImplicitGeometryProperty lod4ImplicitRepresentation = source.getImplicitGeometries()
                    .getFirst(Name.of("lod4ImplicitRepresentation", Namespaces.DEPRECATED))
                    .orElse(null);
            if (lod4ImplicitRepresentation != null) {
                target.getDeprecatedProperties().setLod4ImplicitRepresentation(
                        helper.getImplicitGeometryProperty(lod4ImplicitRepresentation,
                                ImplicitGeometryPropertyAdapter.class));
            }
        }
    }

    @Override
    protected void configureSerializer(SpaceGeometrySupport<SolitaryVegetationObject> geometrySupport) {
        geometrySupport.withLod1Solid()
                .withLod2Solid()
                .withLod2MultiSurface()
                .withLod2MultiCurve()
                .withLod3Solid()
                .withLod3MultiSurface()
                .withLod3MultiCurve()
                .withLod1ImplicitRepresentation()
                .withLod2ImplicitRepresentation()
                .withLod3ImplicitRepresentation();
    }
}
