/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.encoding;

import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.writer.ObjectWriter;
import org.citydb.model.common.Matrix3x4;

import java.lang.reflect.Type;

public class Matrix3x4Writer implements ObjectWriter<Matrix3x4> {
    @Override
    public void write(JSONWriter jsonWriter, Object o, Object o1, Type type, long l) {
        if (o instanceof Matrix3x4 matrix) {
            jsonWriter.write(matrix.toRowMajor());
        } else {
            jsonWriter.writeNull();
        }
    }
}
