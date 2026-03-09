/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.core;

import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.builder.ModelBuilder;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.serializer.ModelSerializer;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.property.Attribute;
import org.citygml4j.core.model.core.AbstractGenericAttribute;

public abstract class AbstractGenericAttributeAdapter<T extends AbstractGenericAttribute<?>> implements ModelBuilder<T, Attribute>, ModelSerializer<Attribute, T> {

    @Override
    public Attribute createModel(T source) throws ModelBuildException {
        return Attribute.of(Name.of(source.getName(), Namespaces.GENERICS));
    }

    @Override
    public void serialize(Attribute source, T target, ModelSerializerHelper helper) throws ModelSerializeException {
        target.setName(source.getName().getLocalName());
    }
}
