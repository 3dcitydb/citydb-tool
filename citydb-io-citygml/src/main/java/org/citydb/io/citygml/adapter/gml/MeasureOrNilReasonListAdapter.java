/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.gml;

import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.builder.ModelBuilder;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.serializer.ModelSerializer;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.property.ArrayValue;
import org.citydb.model.property.Attribute;
import org.citydb.model.property.DataType;
import org.citydb.model.property.Value;
import org.xmlobjects.gml.model.basictypes.DoubleOrNilReason;
import org.xmlobjects.gml.model.basictypes.MeasureOrNilReasonList;
import org.xmlobjects.gml.model.basictypes.NilReason;

public class MeasureOrNilReasonListAdapter implements ModelBuilder<MeasureOrNilReasonList, Attribute>, ModelSerializer<Attribute, MeasureOrNilReasonList> {

    @Override
    public void build(MeasureOrNilReasonList source, Attribute target, ModelBuilderHelper helper) throws ModelBuildException {
        if (source.isSetValue()) {
            ArrayValue arrayValue = ArrayValue.newInstance();
            for (DoubleOrNilReason value : source.getValue()) {
                if (value.isSetValue()) {
                    arrayValue.add(Value.of(value.getValue()));
                } else if (value.isSetNilReason()) {
                    arrayValue.add(Value.of(value.getNilReason().getValue()));
                }
            }

            target.setArrayValue(arrayValue);
        }

        target.setUom(source.getUom())
                .setDataType(DataType.MEASURE_OR_NIL_REASON_LIST);
    }

    @Override
    public MeasureOrNilReasonList createObject(Attribute source) throws ModelSerializeException {
        return new MeasureOrNilReasonList();
    }

    @Override
    public void serialize(Attribute source, MeasureOrNilReasonList target, ModelSerializerHelper helper) throws ModelSerializeException {
        ArrayValue arrayValue = source.getArrayValue().orElse(null);
        if (arrayValue != null) {
            for (Value value : arrayValue.getValues()) {
                if (value.isDouble()) {
                    target.getValue().add(new DoubleOrNilReason(value.doubleValue()));
                } else {
                    target.getValue().add(new DoubleOrNilReason(new NilReason(value.stringValue())));
                }
            }
        }

        source.getUom().ifPresent(target::setUom);
    }
}
