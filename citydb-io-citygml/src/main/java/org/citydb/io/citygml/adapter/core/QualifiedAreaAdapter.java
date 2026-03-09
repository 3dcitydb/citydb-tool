/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.core;

import org.citydb.io.citygml.adapter.gml.AreaAdapter;
import org.citydb.io.citygml.adapter.gml.CodeAdapter;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.builder.ModelBuilder;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.serializer.ModelSerializer;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.property.Attribute;
import org.citydb.model.property.DataType;
import org.citygml4j.core.model.core.QualifiedArea;

public class QualifiedAreaAdapter implements ModelBuilder<QualifiedArea, Attribute>, ModelSerializer<Attribute, QualifiedArea> {

    @Override
    public void build(QualifiedArea source, Attribute target, ModelBuilderHelper helper) throws ModelBuildException {
        if (source.getArea() != null) {
            helper.getOrCreateBuilder(AreaAdapter.class).build(source.getArea(), target, helper);
        }

        if (source.getTypeOfArea() != null) {
            helper.getOrCreateBuilder(CodeAdapter.class).build(source.getTypeOfArea(), target, helper);
        }

        target.setDataType(DataType.QUALIFIED_AREA);
    }

    @Override
    public QualifiedArea createObject(Attribute source) throws ModelSerializeException {
        return new QualifiedArea();
    }

    @Override
    public void serialize(Attribute source, QualifiedArea target, ModelSerializerHelper helper) throws ModelSerializeException {
        target.setArea(helper.getAttribute(source, AreaAdapter.class));
        target.setTypeOfArea(helper.getAttribute(source, CodeAdapter.class));
    }
}
