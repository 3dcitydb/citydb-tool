/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter.encoding;

import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.reader.ObjectReader;
import org.citydb.query.filter.Filter;

import java.lang.reflect.Type;

public class FilterConfigReader implements ObjectReader<Filter> {
    @Override
    public Filter readObject(JSONReader jsonReader, Type type, Object o, long l) {
        try {
            if (jsonReader.isObject()) {
                return Filter.ofJSON(jsonReader.readAny());
            } else if (jsonReader.isString()) {
                return Filter.ofText(jsonReader.readString());
            } else {
                return null;
            }
        } catch (FilterParseException e) {
            throw new JSONException("Failed to parse query filter expression.", e);
        }
    }
}
