/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.config.encoding;

import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.reader.ObjectReader;
import org.citydb.core.time.TimeHelper;

import java.lang.reflect.Type;
import java.time.OffsetDateTime;

public class DateTimeReader implements ObjectReader<OffsetDateTime> {
    @Override
    public OffsetDateTime readObject(JSONReader jsonReader, Type type, Object o, long l) {
        if (jsonReader.isString()) {
            try {
                return OffsetDateTime.parse(jsonReader.readString(), TimeHelper.DATE_TIME_FORMATTER);
            } catch (Exception e) {
                return null;
            }
        } else {
            return null;
        }
    }
}
