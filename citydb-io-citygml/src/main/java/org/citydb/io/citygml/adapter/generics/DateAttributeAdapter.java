/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
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
