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

import org.citydb.io.citygml.adapter.core.SpaceBoundaryGeometrySupport;
import org.citydb.io.citygml.annotation.DatabaseType;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.feature.FeatureType;
import org.citygml4j.core.model.construction.OuterFloorSurface;

@DatabaseType(name = "OuterFloorSurface", namespace = Namespaces.CONSTRUCTION)
public class OuterFloorSurfaceAdapter extends AbstractConstructionSurfaceAdapter<OuterFloorSurface> {

    @Override
    public Feature createModel(OuterFloorSurface source) throws ModelBuildException {
        return Feature.of(FeatureType.OUTER_FLOOR_SURFACE);
    }

    @Override
    public OuterFloorSurface createObject(Feature source) throws ModelSerializeException {
        return new OuterFloorSurface();
    }

    @Override
    protected void configureSerializer(SpaceBoundaryGeometrySupport<OuterFloorSurface> geometrySupport) {
        geometrySupport.withLod2MultiSurface()
                .withLod3MultiSurface();
    }
}
