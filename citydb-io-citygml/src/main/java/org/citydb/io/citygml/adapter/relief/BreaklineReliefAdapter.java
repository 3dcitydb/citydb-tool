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

package org.citydb.io.citygml.adapter.relief;

import org.citydb.io.citygml.adapter.geometry.builder.Lod;
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
import org.citygml4j.core.model.relief.BreaklineRelief;

@DatabaseType(name = "BreaklineRelief", namespace = Namespaces.RELIEF)
public class BreaklineReliefAdapter extends AbstractReliefComponentAdapter<BreaklineRelief> {

    @Override
    public Feature createModel(BreaklineRelief source) throws ModelBuildException {
        return Feature.of(FeatureType.BREAKLINE_REFLIEF);
    }

    @Override
    public void build(BreaklineRelief source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);

        if (source.getRidgeOrValleyLines() != null) {
            helper.addCurveGeometry(Name.of("ridgeOrValleyLines", Namespaces.RELIEF), source.getRidgeOrValleyLines(),
                    Lod.of(source.getLod()), target);
        }

        if (source.getBreaklines() != null) {
            helper.addCurveGeometry(Name.of("breaklines", Namespaces.RELIEF), source.getBreaklines(),
                    Lod.of(source.getLod()), target);
        }
    }

    @Override
    public BreaklineRelief createObject(Feature source) throws ModelSerializeException {
        return new BreaklineRelief();
    }

    @Override
    public void serialize(Feature source, BreaklineRelief target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);

        GeometryProperty ridgeOrValleyLines = source.getGeometries()
                .getFirst(Name.of("ridgeOrValleyLines", Namespaces.RELIEF))
                .orElse(null);
        if (ridgeOrValleyLines != null) {
            target.setRidgeOrValleyLines(helper.getGeometryProperty(ridgeOrValleyLines, MultiCurvePropertyAdapter.class));
        }

        GeometryProperty breaklines = source.getGeometries()
                .getFirst(Name.of("breaklines", Namespaces.RELIEF))
                .orElse(null);
        if (breaklines != null) {
            target.setBreaklines(helper.getGeometryProperty(breaklines, MultiCurvePropertyAdapter.class));
        }
    }
}
