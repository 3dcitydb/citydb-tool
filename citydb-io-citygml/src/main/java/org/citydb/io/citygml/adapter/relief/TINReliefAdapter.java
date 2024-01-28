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

package org.citydb.io.citygml.adapter.relief;

import org.citydb.io.citygml.adapter.geometry.builder.Lod;
import org.citydb.io.citygml.adapter.geometry.serializer.TinPropertyAdapter;
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
import org.citygml4j.core.model.relief.TINRelief;

@DatabaseType(name = "TINRelief", namespace = Namespaces.RELIEF)
public class TINReliefAdapter extends AbstractReliefComponentAdapter<TINRelief> {

    @Override
    public Feature createModel(TINRelief source) throws ModelBuildException {
        return Feature.of(FeatureType.TIN_RELIEF);
    }

    @Override
    public void build(TINRelief source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);

        if (source.getTin() != null) {
            helper.addSurfaceGeometry(Name.of("tin", Namespaces.RELIEF), source.getTin(),
                    Lod.of(source.getLod()), target);
        }
    }

    @Override
    public TINRelief createObject(Feature source) throws ModelSerializeException {
        return new TINRelief();
    }

    @Override
    public void serialize(Feature source, TINRelief target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);

        GeometryProperty tin = source.getGeometries().getFirst(Name.of("tin", Namespaces.RELIEF)).orElse(null);
        if (tin != null) {
            target.setTin(helper.getGeometryProperty(tin, TinPropertyAdapter.class));
        }
    }
}
