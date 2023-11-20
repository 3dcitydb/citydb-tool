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

package org.citydb.io.citygml.adapter.generics;

import org.citydb.io.citygml.adapter.core.AbstractOccupiedSpaceAdapter;
import org.citydb.io.citygml.adapter.core.ImplicitGeometryPropertyAdapter;
import org.citydb.io.citygml.adapter.core.SpaceGeometrySupport;
import org.citydb.io.citygml.adapter.geometry.builder.Lod;
import org.citydb.io.citygml.adapter.geometry.serializer.GeometryPropertyAdapter;
import org.citydb.io.citygml.adapter.geometry.serializer.MultiCurvePropertyAdapter;
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
import org.citygml4j.core.model.deprecated.generics.DeprecatedPropertiesOfGenericOccupiedSpace;
import org.citygml4j.core.model.generics.GenericOccupiedSpace;

@DatabaseType(name = "GenericOccupiedSpace", namespace = Namespaces.GENERICS)
public class GenericOccupiedSpaceAdapter extends AbstractOccupiedSpaceAdapter<GenericOccupiedSpace> {

    @Override
    public Feature createModel(GenericOccupiedSpace source) throws ModelBuildException {
        return Feature.of(FeatureType.GENERIC_OCCUPIED_SPACE);
    }

    @Override
    public void build(GenericOccupiedSpace source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.GENERICS);

        if (source.hasDeprecatedProperties()) {
            DeprecatedPropertiesOfGenericOccupiedSpace properties = source.getDeprecatedProperties();
            if (properties.getLod0Geometry() != null) {
                helper.addGeometry(Name.of("lod0Geometry", Namespaces.DEPRECATED),
                        properties.getLod0Geometry(), Lod.of(0), target);
            }

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

            if (properties.getLod0TerrainIntersectionCurve() != null) {
                helper.addCurveGeometry(Name.of("lod0TerrainIntersectionCurve", Namespaces.DEPRECATED),
                        properties.getLod0TerrainIntersectionCurve(), Lod.of(0), target);
            }

            if (properties.getLod4TerrainIntersectionCurve() != null) {
                helper.addCurveGeometry(Name.of("lod4TerrainIntersectionCurve", Namespaces.DEPRECATED),
                        properties.getLod4TerrainIntersectionCurve(), Lod.of(4), target);
            }

            if (properties.getLod0ImplicitRepresentation() != null) {
                helper.addImplicitGeometry(Name.of("lod0ImplicitRepresentation", Namespaces.DEPRECATED),
                        properties.getLod0ImplicitRepresentation(), Lod.of(0), target);
            }

            if (properties.getLod4ImplicitRepresentation() != null) {
                helper.addImplicitGeometry(Name.of("lod4ImplicitRepresentation", Namespaces.DEPRECATED),
                        properties.getLod4ImplicitRepresentation(), Lod.of(4), target);
            }
        }
    }

    @Override
    public GenericOccupiedSpace createObject(Feature source) throws ModelSerializeException {
        return new GenericOccupiedSpace();
    }

    @Override
    public void serialize(Feature source, GenericOccupiedSpace target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.GENERICS);
    }

    @Override
    public void postSerialize(Feature source, GenericOccupiedSpace target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.postSerialize(source, target, helper);

        if (helper.getCityGMLVersion() == CityGMLVersion.v2_0
                && source.getGeometries().containsNamespace(Namespaces.DEPRECATED)) {
            GeometryProperty lod0Geometry = source.getGeometries()
                    .getFirst(Name.of("lod0Geometry", Namespaces.DEPRECATED))
                    .orElse(null);
            if (lod0Geometry != null) {
                target.getDeprecatedProperties().setLod0Geometry(
                        helper.getGeometryProperty(lod0Geometry, GeometryPropertyAdapter.class));
            }

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

            GeometryProperty lod0TerrainIntersectionCurve = source.getGeometries()
                    .getFirst(Name.of("lod0TerrainIntersectionCurve", Namespaces.DEPRECATED))
                    .orElse(null);
            if (lod0TerrainIntersectionCurve != null) {
                target.getDeprecatedProperties().setLod0TerrainIntersectionCurve(
                        helper.getGeometryProperty(lod0TerrainIntersectionCurve, MultiCurvePropertyAdapter.class));
            }

            GeometryProperty lod4TerrainIntersectionCurve = source.getGeometries()
                    .getFirst(Name.of("lod4TerrainIntersectionCurve", Namespaces.DEPRECATED))
                    .orElse(null);
            if (lod4TerrainIntersectionCurve != null) {
                target.getDeprecatedProperties().setLod4TerrainIntersectionCurve(
                        helper.getGeometryProperty(lod4TerrainIntersectionCurve, MultiCurvePropertyAdapter.class));
            }

            ImplicitGeometryProperty lod0ImplicitRepresentation = source.getImplicitGeometries()
                    .getFirst(Name.of("lod0ImplicitRepresentation", Namespaces.DEPRECATED))
                    .orElse(null);
            if (lod0ImplicitRepresentation != null) {
                target.getDeprecatedProperties().setLod0ImplicitRepresentation(
                        helper.getImplicitGeometryProperty(lod0ImplicitRepresentation,
                                ImplicitGeometryPropertyAdapter.class));
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
    protected void configureSerializer(SpaceGeometrySupport<GenericOccupiedSpace> geometrySupport) {
        geometrySupport.withLod0Point()
                .withLod0MultiSurface()
                .withLod0MultiCurve()
                .withLod1Solid()
                .withLod2Solid()
                .withLod2MultiSurface()
                .withLod2MultiCurve()
                .withLod3Solid()
                .withLod3MultiSurface()
                .withLod3MultiCurve()
                .withLod1TerrainIntersectionCurve()
                .withLod2TerrainIntersectionCurve()
                .withLod3TerrainIntersectionCurve()
                .withLod1ImplicitRepresentation()
                .withLod2ImplicitRepresentation()
                .withLod3ImplicitRepresentation();
    }
}
