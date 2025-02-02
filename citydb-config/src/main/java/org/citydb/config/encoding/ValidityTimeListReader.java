/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
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

package org.citydb.config.encoding;

import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.reader.ObjectReader;
import org.citydb.core.time.TimeHelper;

import java.lang.reflect.Type;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class ValidityTimeListReader implements ObjectReader<List<OffsetDateTime>> {
    @Override
    public List<OffsetDateTime> readObject(JSONReader jsonReader, Type type, Object o, long l) {
        if (jsonReader.isArray()) {
            List<OffsetDateTime> result = new ArrayList<>();
            for (Object value : jsonReader.readArray()) {
                if (value instanceof String dateTime) {
                    try {
                        result.add(OffsetDateTime.parse(dateTime, TimeHelper.VALIDITY_TIME_FORMATTER));
                    } catch (Exception e) {
                        //
                    }
                }
            }

            return result;
        } else {
            return null;
        }
    }
}
