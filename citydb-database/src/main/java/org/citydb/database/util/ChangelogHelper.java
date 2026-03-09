/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.util;

import org.citydb.sqlbuilder.query.Select;
import org.citydb.sqlbuilder.query.Selection;

import java.sql.Connection;
import java.sql.SQLException;

public interface ChangelogHelper {
    boolean isChangelogEnabled(String schemaName, Connection connection) throws SQLException;

    Select getChangeRegions(Select baseQuery, Selection<?> envelope);

    Select getAndTransformChangeRegions(Select baseQuery, Selection<?> envelope, int srid);
}
