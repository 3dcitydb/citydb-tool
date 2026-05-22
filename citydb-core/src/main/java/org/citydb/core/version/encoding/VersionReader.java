/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.core.version.encoding;

import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.reader.ObjectReader;
import org.citydb.core.version.Version;

import java.lang.reflect.Type;

public class VersionReader implements ObjectReader<Version> {
    @Override
    public Version readObject(JSONReader jsonReader, Type type, Object o, long l) {
        return jsonReader.isString()
                ? Version.parse(jsonReader.readString()).orElse(null)
                : null;
    }
}
