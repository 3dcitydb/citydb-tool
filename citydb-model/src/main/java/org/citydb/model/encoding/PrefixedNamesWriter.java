/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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

public class PrefixedNamesWriter implements ObjectWriter<List<PrefixedName>> {
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
