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

package org.citydb.io.citygml.adapter.bridge;

import org.citydb.io.citygml.adapter.construction.AbstractFurnitureAdapter;
import org.citydb.io.citygml.adapter.core.ImplicitGeometryPropertyAdapter;
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
import org.citygml4j.core.model.bridge.BridgeFurniture;
import org.citygml4j.core.model.deprecated.bridge.DeprecatedPropertiesOfBridgeFurniture;

@DatabaseType(name = "BridgeFurniture", namespace = Namespaces.BRIDGE)
public class BridgeFurnitureAdapter extends AbstractFurnitureAdapter<BridgeFurniture> {

    @Override
    public Feature createModel(BridgeFurniture source) throws ModelBuildException {
        return Feature.of(FeatureType.BRIDGE_FURNITURE);
    }

    @Override
    public void build(BridgeFurniture source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.BRIDGE);

        if (source.hasDeprecatedProperties()) {
            DeprecatedPropertiesOfBridgeFurniture properties = source.getDeprecatedProperties();
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
    public BridgeFurniture createObject(Feature source) throws ModelSerializeException {
        return new BridgeFurniture();
    }

    @Override
    public void serialize(Feature source, BridgeFurniture target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.BRIDGE);
    }

    @Override
    public void postSerialize(Feature source, BridgeFurniture target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.postSerialize(source, target, helper);

        if (helper.getCityGMLVersion() == CityGMLVersion.v2_0
                && source.getGeometries().containsNamespace(Namespaces.DEPRECATED)) {
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
}
