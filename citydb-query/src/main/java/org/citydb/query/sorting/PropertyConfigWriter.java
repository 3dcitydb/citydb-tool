/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.sorting;

import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.writer.ObjectWriter;
import org.citydb.query.filter.encoding.FilterTextWriter;
import org.citydb.query.filter.literal.PropertyRef;

import java.lang.reflect.Type;

public class PropertyConfigWriter implements ObjectWriter<PropertyRef> {
    @Override
    public void write(JSONWriter jsonWriter, Object o, Object o1, Type type, long l) {
        if (o instanceof PropertyRef propertyRef) {
            jsonWriter.writeString(FilterTextWriter.newInstance().write(propertyRef));
        } else {
            jsonWriter.writeNull();
        }
    }
}
