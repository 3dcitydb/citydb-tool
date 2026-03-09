/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.generics;

import org.citydb.io.citygml.adapter.core.AbstractGenericAttributeAdapter;
import org.citydb.io.citygml.annotation.DatabaseType;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.common.Namespaces;
import org.citydb.model.property.Attribute;
import org.citydb.model.property.DataType;
import org.citygml4j.core.model.generics.UriAttribute;

@DatabaseType(name = "UriAttribute", namespace = Namespaces.GENERICS)
public class UriAttributeAdapter extends AbstractGenericAttributeAdapter<UriAttribute> {

    @Override
    public void build(UriAttribute source, Attribute target, ModelBuilderHelper helper) throws ModelBuildException {
        target.setURI(source.getValue())
                .setDataType(DataType.URI);
    }

    @Override
    public UriAttribute createObject(Attribute source) throws ModelSerializeException {
        return new UriAttribute();
    }

    @Override
    public void serialize(Attribute source, UriAttribute target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);
        source.getURI().ifPresent(target::setValue);
    }
}
