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
