/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
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

import org.citydb.io.citygml.adapter.construction.AbstractInstallationAdapter;
import org.citydb.io.citygml.adapter.core.ImplicitGeometryPropertyAdapter;
import org.citydb.io.citygml.adapter.core.SpaceGeometrySupport;
import org.citydb.io.citygml.adapter.geometry.builder.Lod;
import org.citydb.io.citygml.adapter.geometry.serializer.GeometryPropertyAdapter;
import org.citydb.io.citygml.annotation.DatabaseType;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.feature.FeatureType;
import org.citydb.model.property.GeometryProperty;
import org.citydb.model.property.ImplicitGeometryProperty;
import org.citygml4j.core.model.CityGMLVersion;
import org.citygml4j.core.model.building.BuildingInstallation;
import org.citygml4j.core.model.construction.RelationToConstruction;
import org.citygml4j.core.model.deprecated.building.DeprecatedPropertiesOfBuildingInstallation;

import java.util.function.BiFunction;

@DatabaseType(name = "BuildingInstallation", namespace = Namespaces.BUILDING)
public class BuildingInstallationAdapter extends AbstractInstallationAdapter<BuildingInstallation> {

    @Override
    public Feature createModel(BuildingInstallation source) throws ModelBuildException {
        return Feature.of(FeatureType.BUILDING_INSTALLATION);
    }

    @Override
    public void build(BuildingInstallation source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.BUILDING);

        if (source.hasDeprecatedProperties()) {
            DeprecatedPropertiesOfBuildingInstallation properties = source.getDeprecatedProperties();
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
    public BuildingInstallation createObject(Feature source) throws ModelSerializeException {
        return new BuildingInstallation();
    }

    @Override
    public void serialize(Feature source, BuildingInstallation target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.BUILDING);
    }

    @Override
    public void postSerialize(Feature source, BuildingInstallation target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.postSerialize(source, target, helper);

        if (helper.getCityGMLVersion() == CityGMLVersion.v2_0
                && source.getGeometries().containsNamespace(Namespaces.DEPRECATED)) {
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
    protected void configureSerializer(SpaceGeometrySupport<BuildingInstallation> geometrySupport) {
        BiFunction<CityGMLVersion, BuildingInstallation, Boolean> predicate =
                (version, target) -> target.getRelationToConstruction() != RelationToConstruction.INSIDE;
        geometrySupport.withLod2Solid(predicate)
                .withLod2MultiSurface(predicate)
                .withLod2MultiCurve(predicate)
                .withLod3Solid(predicate)
                .withLod3MultiSurface(predicate)
                .withLod3MultiCurve(predicate)
                .withLod2ImplicitRepresentation(predicate)
                .withLod3ImplicitRepresentation(predicate);
    }
}
