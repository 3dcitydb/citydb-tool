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

package org.citydb.io.citygml.adapter.construction;

import org.citydb.io.citygml.adapter.core.AbstractThematicSurfaceAdapter;
import org.citydb.io.citygml.adapter.core.ImplicitGeometryPropertyAdapter;
import org.citydb.io.citygml.adapter.core.SpaceBoundaryGeometrySupport;
import org.citydb.io.citygml.adapter.geometry.builder.Lod;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.property.ImplicitGeometryProperty;
import org.citygml4j.core.model.CityGMLVersion;
import org.citygml4j.core.model.construction.AbstractFillingSurface;
import org.citygml4j.core.model.deprecated.construction.DeprecatedPropertiesOfAbstractFillingSurface;

public abstract class AbstractFillingSurfaceAdapter<T extends AbstractFillingSurface> extends AbstractThematicSurfaceAdapter<T> {

    @Override
    public void build(T source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);

        if (source.hasDeprecatedProperties()) {
            DeprecatedPropertiesOfAbstractFillingSurface properties = source.getDeprecatedProperties();
            if (properties.getLod3ImplicitRepresentation() != null) {
                helper.addImplicitGeometry(Name.of("lod3ImplicitRepresentation", Namespaces.DEPRECATED),
                        properties.getLod3ImplicitRepresentation(), Lod.of(3), target);
            }

            if (properties.getLod4ImplicitRepresentation() != null) {
                helper.addImplicitGeometry(Name.of("lod4ImplicitRepresentation", Namespaces.DEPRECATED),
                        properties.getLod4ImplicitRepresentation(), Lod.of(4), target);
            }
        }
    }

    @Override
    public void serialize(Feature source, T target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);

        if (helper.getCityGMLVersion() == CityGMLVersion.v2_0
                && source.getGeometries().containsNamespace(Namespaces.DEPRECATED)) {
            ImplicitGeometryProperty lod3ImplicitRepresentation = source.getImplicitGeometries()
                    .getFirst(Name.of("lod3ImplicitRepresentation", Namespaces.DEPRECATED))
                    .orElse(null);
            if (lod3ImplicitRepresentation != null) {
                target.getDeprecatedProperties().setLod3ImplicitRepresentation(
                        helper.getImplicitGeometryProperty(lod3ImplicitRepresentation,
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
    protected void configureSerializer(SpaceBoundaryGeometrySupport<T> geometrySupport) {
        geometrySupport.withLod3MultiSurface();
    }
}
