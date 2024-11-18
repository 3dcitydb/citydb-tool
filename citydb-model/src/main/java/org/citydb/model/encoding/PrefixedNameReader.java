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

package org.citydb.model.encoding;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.reader.ObjectReader;
import org.citydb.model.common.PrefixedName;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class PrefixedNameReader implements ObjectReader<List<PrefixedName>> {
    @Override
    public List<PrefixedName> readObject(JSONReader jsonReader, Type type, Object o, long l) {
        if (jsonReader.isArray()) {
            List<PrefixedName> featureTypes = new ArrayList<>();
            for (Object item : jsonReader.readArray()) {
                if (item instanceof JSONObject object) {
                    String name = object.getString("name");
                    if (name != null) {
                        featureTypes.add(PrefixedName.of(name, object.getString("namespace")));
                    }
                }
            }

            return featureTypes;
        } else {
            return null;
        }
    }
}
