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
import org.citygml4j.core.model.building.BuildingUnit;
import org.citygml4j.core.model.building.StoreyProperty;
import org.citygml4j.core.model.core.AddressProperty;

@DatabaseType(name = "BuildingUnit", namespace = Namespaces.BUILDING)
public class BuildingUnitAdapter extends AbstractBuildingSubdivisionAdapter<BuildingUnit> {

    @Override
    public Feature createModel(BuildingUnit source) throws ModelBuildException {
        return Feature.of(FeatureType.BUILDING_UNIT);
    }

    @Override
    public void build(BuildingUnit source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);

        if (source.isSetStoreys()) {
            for (StoreyProperty property : source.getStoreys()) {
                helper.addContainedFeature(Name.of("storey", Namespaces.BUILDING), property, target);
            }
        }

        if (source.isSetAddresses()) {
            for (AddressProperty property : source.getAddresses()) {
                helper.addAddress(Name.of("address", Namespaces.BUILDING), property, target);
            }
        }
    }

    @Override
    public BuildingUnit createObject(Feature source) throws ModelSerializeException {
        return new BuildingUnit();
    }

    @Override
    public void serialize(Feature source, BuildingUnit target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);

        for (FeatureProperty property : source.getFeatures().get(Name.of("storey", Namespaces.BUILDING))) {
            target.getStoreys().add(helper.getObjectProperty(property, StoreyPropertyAdapter.class));
        }

        for (org.citydb.model.property.AddressProperty property : source.getAddresses().getAll()) {
            target.getAddresses().add(helper.getAddressProperty(property));
        }
    }
}
