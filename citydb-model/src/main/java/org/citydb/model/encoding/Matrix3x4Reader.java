/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.encoding;

import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.reader.ObjectReader;
import org.citydb.model.common.Matrix3x4;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class Matrix3x4Reader implements ObjectReader<Matrix3x4> {
    @Override
    public Matrix3x4 readObject(JSONReader jsonReader, Type type, Object o, long l) {
        if (jsonReader.isArray()) {
            List<Double> values = new ArrayList<>();
            for (Object value : jsonReader.readArray()) {
                if (value instanceof Number number) {
                    values.add(number.doubleValue());
                }
            }

            if (values.size() > 11) {
                return Matrix3x4.ofRowMajor(values);
            }
        }

        return null;
    }
}
