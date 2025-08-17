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

package org.citydb.io.citygml.adapter.core;

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
import org.citygml4j.core.model.core.AbstractOccupiedSpace;

public abstract class AbstractOccupiedSpaceAdapter<T extends AbstractOccupiedSpace> extends AbstractPhysicalSpaceAdapter<T> {

    @Override
    public void build(T source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);

        if (source.getLod1ImplicitRepresentation() != null) {
            helper.addImplicitGeometry(Name.of("lod1ImplicitRepresentation", Namespaces.CORE),
                    source.getLod1ImplicitRepresentation(), Lod.of(1), target);
        }

        if (source.getLod2ImplicitRepresentation() != null) {
            helper.addImplicitGeometry(Name.of("lod2ImplicitRepresentation", Namespaces.CORE),
                    source.getLod2ImplicitRepresentation(), Lod.of(2), target);
        }

        if (source.getLod3ImplicitRepresentation() != null) {
            helper.addImplicitGeometry(Name.of("lod3ImplicitRepresentation", Namespaces.CORE),
                    source.getLod3ImplicitRepresentation(), Lod.of(3), target);
        }
    }

    @Override
    public void postSerialize(Feature source, T target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.postSerialize(source, target, helper);
        CityGMLVersion version = helper.getCityGMLVersion();
        boolean isCityGML3 = version == CityGMLVersion.v3_0;

        if (isCityGML3 || geometrySupport.supportsLod1ImplicitRepresentation(version, target)) {
            ImplicitGeometryProperty lod1ImplicitRepresentation = source.getImplicitGeometries()
                    .getFirst(Name.of("lod1ImplicitRepresentation", Namespaces.CORE))
                    .orElse(null);
            if (lod1ImplicitRepresentation != null) {
                target.setLod1ImplicitRepresentation(helper.getImplicitGeometryProperty(lod1ImplicitRepresentation,
                        ImplicitGeometryPropertyAdapter.class));
            }
        }

        if (isCityGML3 || geometrySupport.supportsLod2ImplicitRepresentation(version, target)) {
            ImplicitGeometryProperty lod2ImplicitRepresentation = source.getImplicitGeometries()
                    .getFirst(Name.of("lod2ImplicitRepresentation", Namespaces.CORE))
                    .orElse(null);
            if (lod2ImplicitRepresentation != null) {
                target.setLod2ImplicitRepresentation(helper.getImplicitGeometryProperty(lod2ImplicitRepresentation,
                        ImplicitGeometryPropertyAdapter.class));
            }
        }

        if (isCityGML3 || geometrySupport.supportsLod3ImplicitRepresentation(version, target)) {
            ImplicitGeometryProperty lod3ImplicitRepresentation = source.getImplicitGeometries()
                    .getFirst(helper.isUseLod4AsLod3() ?
                            Name.of("lod4ImplicitRepresentation", Namespaces.DEPRECATED) :
                            Name.of("lod3ImplicitRepresentation", Namespaces.CORE))
                    .orElse(null);
            if (lod3ImplicitRepresentation != null) {
                target.setLod3ImplicitRepresentation(helper.getImplicitGeometryProperty(lod3ImplicitRepresentation,
                        ImplicitGeometryPropertyAdapter.class));
            }
        }
    }
}
