/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.address;

import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.serializer.ModelSerializer;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.address.Address;
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
            source.getReference().ifPresent(reference -> target.setHref("#" + reference));
        }
    }
}
