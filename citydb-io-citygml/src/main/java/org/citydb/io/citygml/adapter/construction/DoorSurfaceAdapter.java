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

import org.citydb.io.citygml.annotation.DatabaseType;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.feature.FeatureType;
import org.citygml4j.core.model.construction.DoorSurface;
import org.citygml4j.core.model.core.AddressProperty;

@DatabaseType(name = "DoorSurface", namespace = Namespaces.CONSTRUCTION)
public class DoorSurfaceAdapter extends AbstractFillingSurfaceAdapter<DoorSurface> {

    @Override
    public Feature createModel(DoorSurface source) throws ModelBuildException {
        return Feature.of(FeatureType.DOOR_SURFACE);
    }

    @Override
    public void build(DoorSurface source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);

        if (source.isSetAddresses()) {
            for (AddressProperty property : source.getAddresses()) {
                helper.addAddress(Name.of("address", Namespaces.CONSTRUCTION), property, target);
            }
        }
    }

    @Override
    public DoorSurface createObject(Feature source) throws ModelSerializeException {
        return new DoorSurface();
    }

    @Override
    public void serialize(Feature source, DoorSurface target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);

        for (org.citydb.model.property.AddressProperty property : source.getAddresses().getAll()) {
            target.getAddresses().add(helper.getAddressProperty(property));
        }
    }
}
