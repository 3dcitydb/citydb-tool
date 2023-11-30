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

package org.citydb.io.citygml.adapter.relief;

import org.citydb.io.citygml.adapter.core.AbstractSpaceBoundaryAdapter;
import org.citydb.io.citygml.adapter.geometry.builder.Lod;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.Geometry;
import org.citydb.model.geometry.GeometryType;
import org.citydb.model.geometry.Polygon;
import org.citydb.model.property.Attribute;
import org.citydb.model.property.DataType;
import org.citydb.model.property.GeometryProperty;
import org.citygml4j.core.model.relief.AbstractReliefComponent;
import org.citygml4j.core.model.relief.ExtentProperty;

public abstract class AbstractReliefComponentAdapter<T extends AbstractReliefComponent> extends AbstractSpaceBoundaryAdapter<T> {

    @Override
    public void build(T source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);

        target.addAttribute(Attribute.of(Name.of("lod", Namespaces.RELIEF), DataType.INTEGER)
                .setIntValue(source.getLod()));

        if (source.getExtent() != null) {
            helper.addSurfaceGeometry(Name.of("extent", Namespaces.RELIEF), source.getExtent(),
                    Lod.NONE, true, target);
        }
    }

    @Override
    public void serialize(Feature source, T target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);

        source.getAttributes().getFirst(Name.of("lod", Namespaces.RELIEF))
                .flatMap(Attribute::getIntValue)
                .ifPresent(value -> target.setLod(value.intValue()));

        Geometry<?> geometry = source.getGeometries().getFirst(Name.of("extent", Namespaces.RELIEF))
                .map(GeometryProperty::getObject)
                .orElse(null);
        if (geometry != null && geometry.getGeometryType() == GeometryType.POLYGON) {
            Polygon extent = (Polygon) geometry;
            if (helper.lookupAndPut(extent)) {
                target.setExtent(new ExtentProperty("#" + extent.getOrCreateObjectId()));
            } else {
                target.setExtent(new ExtentProperty(helper.getPolygon(extent.force2D(), false)));
            }
        }
    }
}
