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

package org.citydb.io.citygml.adapter.address;

import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.serializer.ModelSerializer;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.address.Address;
import org.citydb.model.common.Reference;
import org.citygml4j.core.model.core.AddressProperty;

public class AddressPropertyAdapter implements ModelSerializer<org.citydb.model.property.AddressProperty, AddressProperty> {

    @Override
    public AddressProperty createObject(org.citydb.model.property.AddressProperty source) throws ModelSerializeException {
        return new AddressProperty();
    }

    @Override
    public void serialize(org.citydb.model.property.AddressProperty source, AddressProperty target, ModelSerializerHelper helper) throws ModelSerializeException {
        Address address = source.getObject().orElse(null);
        if (address != null) {
            if (helper.lookupAndPut(address)) {
                target.setHref("#" + address.getOrCreateObjectId());
            } else {
                target.setInlineObjectIfValid(helper.getAddress(address));
            }
        } else {
            source.getReference().map(Reference::getTarget).ifPresent(reference -> target.setHref("#" + reference));
        }
    }
}
