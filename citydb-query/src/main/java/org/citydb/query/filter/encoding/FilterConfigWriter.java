/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter.encoding;

import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.writer.ObjectWriter;
import org.citydb.query.filter.Filter;

import java.lang.reflect.Type;

public class FilterConfigWriter implements ObjectWriter<Filter> {
    @Override
    public void write(JSONWriter jsonWriter, Object o, Object o1, Type type, long l) {
        if (o instanceof Filter filter) {
            FilterJSONWriter.newInstance().write(filter.getExpression(), jsonWriter);
        } else {
            jsonWriter.writeNull();
        }
    }
}
