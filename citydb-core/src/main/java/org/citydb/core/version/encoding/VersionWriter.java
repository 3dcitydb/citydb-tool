/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.core.version.encoding;

import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.writer.ObjectWriter;
import org.citydb.core.version.Version;

import java.lang.reflect.Type;

public class VersionWriter implements ObjectWriter<Version> {
    @Override
    public void write(JSONWriter jsonWriter, Object o, Object o1, Type type, long l) {
        if (o instanceof Version version) {
            jsonWriter.writeString(version.toVersionString());
        } else {
            jsonWriter.writeNull();
        }
    }
}
