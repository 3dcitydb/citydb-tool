/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.generics;

import org.citydb.io.citygml.adapter.core.AbstractGenericAttributeAdapter;
import org.citydb.io.citygml.adapter.gml.MeasureAdapter;
import org.citydb.io.citygml.annotation.DatabaseType;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.common.Namespaces;
import org.citydb.model.property.Attribute;
import org.citydb.model.property.DataType;
import org.citygml4j.core.model.generics.MeasureAttribute;

@DatabaseType(name = "MeasureAttribute", namespace = Namespaces.GENERICS)
public class MeasureAttributeAdapter extends AbstractGenericAttributeAdapter<MeasureAttribute> {

    @Override
    public void build(MeasureAttribute source, Attribute target, ModelBuilderHelper helper) throws ModelBuildException {
        if (source.getValue() != null) {
            target.setDoubleValue(source.getValue().getValue())
                    .setUom(source.getValue().getUom());
        }

        target.setDataType(DataType.MEASURE);
    }

    @Override
    public MeasureAttribute createObject(Attribute source) throws ModelSerializeException {
        return new MeasureAttribute();
    }

    @Override
    public void serialize(Attribute source, MeasureAttribute target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);
        target.setValue(helper.getAttribute(source, MeasureAdapter.class));
    }
}
