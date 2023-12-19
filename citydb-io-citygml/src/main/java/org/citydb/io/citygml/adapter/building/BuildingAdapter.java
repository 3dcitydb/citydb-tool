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

package org.citydb.io.citygml.adapter.building;

import org.citydb.io.citygml.annotation.DatabaseType;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.feature.FeatureType;
import org.citydb.model.property.FeatureProperty;
import org.citygml4j.core.model.building.Building;
import org.citygml4j.core.model.building.BuildingPartProperty;

@DatabaseType(name = "Building", namespace = Namespaces.BUILDING)
public class BuildingAdapter extends AbstractBuildingAdapter<Building> {

    @Override
    public Feature createModel(Building source) throws ModelBuildException {
        return Feature.of(FeatureType.BUILDING);
    }

    @Override
    public void build(Building source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);

        if (source.isSetBuildingParts()) {
            for (BuildingPartProperty property : source.getBuildingParts()) {
                helper.addContainedFeature(Name.of("buildingPart", Namespaces.BUILDING), property, target);
            }
        }
    }

    @Override
    public Building createObject(Feature source) throws ModelSerializeException {
        return new Building();
    }

    @Override
    public void serialize(Feature source, Building target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);

        for (FeatureProperty property : source.getFeatures().get(Name.of("buildingPart", Namespaces.BUILDING))) {
            target.getBuildingParts().add(helper.getObjectProperty(property, BuildingPartPropertyAdapter.class));
        }
    }
}
