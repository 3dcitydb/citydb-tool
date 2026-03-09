/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.generics;

import org.citydb.core.time.TimeHelper;
import org.citydb.io.citygml.adapter.core.AbstractGenericAttributeAdapter;
import org.citydb.io.citygml.annotation.DatabaseType;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.common.Namespaces;
import org.citydb.model.property.Attribute;
import org.citydb.model.property.DataType;
import org.citygml4j.core.model.generics.DateAttribute;

@DatabaseType(name = "DateAttribute", namespace = Namespaces.GENERICS)
public class DateAttributeAdapter extends AbstractGenericAttributeAdapter<DateAttribute> {

    @Override
    public void build(DateAttribute source, Attribute target, ModelBuilderHelper helper) throws ModelBuildException {
        if (source.getValue() != null) {
            target.setTimeStamp(TimeHelper.toDateTime(source.getValue()));
        }

        target.setDataType(DataType.TIMESTAMP);
    }

    @Override
    public DateAttribute createObject(Attribute source) throws ModelSerializeException {
        return new DateAttribute();
    }

    @Override
    public void serialize(Attribute source, DateAttribute target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);
        source.getTimeStamp().ifPresent(timeStamp -> target.setValue(timeStamp.toLocalDate()));
    }
}
