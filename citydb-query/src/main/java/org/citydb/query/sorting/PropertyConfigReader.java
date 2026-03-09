/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.sorting;

import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.reader.ObjectReader;
import org.citydb.query.filter.encoding.FilterParseException;
import org.citydb.query.filter.encoding.FilterTextParser;
import org.citydb.query.filter.literal.PropertyRef;

import java.lang.reflect.Type;

public class PropertyConfigReader implements ObjectReader<PropertyRef> {
    @Override
    public PropertyRef readObject(JSONReader jsonReader, Type type, Object o, long l) {
        if (jsonReader.isString()) {
            try {
                return FilterTextParser.newInstance().parse(jsonReader.readString(), PropertyRef.class);
            } catch (FilterParseException e) {
                throw new JSONException("Failed to parse property reference.", e);
            }
        } else {
            return null;
        }
    }
}
