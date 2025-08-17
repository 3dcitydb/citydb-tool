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

package org.citydb.io.citygml.adapter.landuse;

import org.citydb.io.citygml.adapter.core.AbstractThematicSurfaceAdapter;
import org.citydb.io.citygml.adapter.core.SpaceBoundaryGeometrySupport;
import org.citydb.io.citygml.annotation.DatabaseType;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.feature.FeatureType;
import org.citygml4j.core.model.landuse.LandUse;

@DatabaseType(name = "LandUse", namespace = Namespaces.LAND_USE)
public class LandUseAdapter extends AbstractThematicSurfaceAdapter<LandUse> {

    @Override
    public Feature createModel(LandUse source) throws ModelBuildException {
        return Feature.of(FeatureType.LAND_USE);
    }

    @Override
    public void build(LandUse source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.LAND_USE);
    }

    @Override
    public LandUse createObject(Feature source) throws ModelSerializeException {
        return new LandUse();
    }

    @Override
    public void serialize(Feature source, LandUse target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.LAND_USE);
    }

    @Override
    protected void configureSerializer(SpaceBoundaryGeometrySupport<LandUse> geometrySupport) {
        geometrySupport.withLod0MultiSurface()
                .withLod1MultiSurface()
                .withLod2MultiSurface()
                .withLod3MultiSurface();
    }
}
