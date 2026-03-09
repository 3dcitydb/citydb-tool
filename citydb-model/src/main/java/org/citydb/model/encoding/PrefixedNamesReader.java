/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.encoding;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.reader.ObjectReader;
import org.citydb.model.common.PrefixedName;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class PrefixedNamesReader implements ObjectReader<List<PrefixedName>> {
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
