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
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.writer.ObjectWriter;
import org.citydb.model.common.Namespaces;
import org.citydb.model.common.PrefixedName;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class PrefixedNameWriter implements ObjectWriter<List<PrefixedName>> {
    @Override
    public void write(JSONWriter jsonWriter, Object o, Object o1, Type type, long l) {
        if (o instanceof List<?> list) {
            List<JSONObject> names = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof PrefixedName name) {
                    JSONObject object = new JSONObject();
                    object.put("name", name.getPrefix()
                            .map(prefix -> prefix + ":" + name.getLocalName())
                            .orElse(name.getLocalName()));
                    if (!name.getNamespace().equals(Namespaces.EMPTY_NAMESPACE)) {
                        object.put("namespace", name.getNamespace());
                    }

                    names.add(object);
                }
            }

            jsonWriter.write(names);
        } else {
            jsonWriter.writeArrayNull();
        }
    }
}
