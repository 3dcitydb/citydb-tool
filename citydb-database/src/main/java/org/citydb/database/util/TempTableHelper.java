/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.util;

import java.util.Map;

public interface TempTableHelper {
    String getInteger();

    String getLong();

    String getDouble();

    String getNumeric();

    String getNumeric(int precision);

    String getNumeric(int precision, int scale);

    String getString();

    String getString(int size);

    String getTimeStamp();

    String getTimeStampWithTimeZone();

    String getGeometry();

    String getCreateTempTable(String name, Map<String, String> columns);
}
