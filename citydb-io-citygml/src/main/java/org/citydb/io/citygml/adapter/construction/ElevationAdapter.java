/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.construction;

import org.citydb.io.citygml.adapter.gml.CodeAdapter;
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
import org.citygml4j.core.model.construction.Elevation;
import org.xmlobjects.gml.model.geometry.DirectPosition;

import java.util.stream.Collectors;

public class ElevationAdapter implements ModelBuilder<Elevation, Attribute>, ModelSerializer<Attribute, Elevation> {

    @Override
    public void build(Elevation source, Attribute target, ModelBuilderHelper helper) throws ModelBuildException {
        if (source.getElevationReference() != null) {
            target.setStringValue(source.getElevationReference().getValue())
                    .setCodeSpace(source.getElevationReference().getCodeSpace());
        }

        if (source.getElevationValue() != null) {
            target.setArrayValue(ArrayValue.ofDouble(source.getElevationValue().toCoordinateList3D()));
        }

        target.setDataType(DataType.ELEVATION);
    }

    @Override
    public Elevation createObject(Attribute source) throws ModelSerializeException {
        return new Elevation();
    }

    @Override
    public void serialize(Attribute source, Elevation target, ModelSerializerHelper helper) throws ModelSerializeException {
        target.setElevationReference(helper.getAttribute(source, CodeAdapter.class));

        source.getArrayValue().ifPresent(value -> target.setElevationValue(
                new DirectPosition(value.getValues().stream()
                        .filter(Value::isDouble)
                        .map(Value::doubleValue)
                        .collect(Collectors.toList()))));
    }
}
