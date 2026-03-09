/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.postgres;

import org.citydb.database.adapter.DatabaseAdapter;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collection;
import java.util.function.IntFunction;

public class SqlHelper extends org.citydb.database.util.SqlHelper {

    SqlHelper(DatabaseAdapter adapter) {
        super(adapter);
    }

    @Override
    public void setBytesOrNull(PreparedStatement stmt, int index, byte[] bytes) throws SQLException {
        if (bytes != null) {
            stmt.setBytes(index, bytes);
        } else {
            stmt.setNull(index, Types.BINARY);
        }
    }

    @Override
    public void setJsonOrNull(PreparedStatement stmt, int index, String json) throws SQLException {
        if (json != null) {
            stmt.setObject(index, json, Types.OTHER);
        } else {
            stmt.setNull(index, Types.OTHER);
        }
    }

    @Override
    public void setGeometryOrNull(PreparedStatement stmt, int index, Object geometry) throws SQLException {
        if (geometry != null) {
            stmt.setObject(index, geometry, Types.OTHER);
        } else {
            stmt.setNull(index, Types.OTHER);
        }
    }

    @Override
    public void setLongArrayOrNull(PreparedStatement stmt, int index, Collection<Long> values) throws SQLException {
        setArrayOrNull(stmt, index, values, "bigint", Long[]::new);
    }

    @Override
    public void setStringArrayOrNull(PreparedStatement stmt, int index, Collection<String> values) throws SQLException {
        setArrayOrNull(stmt, index, values, "text", String[]::new);
    }

    private <T> void setArrayOrNull(PreparedStatement stmt, int index, Collection<T> values, String type, IntFunction<T[]> factory) throws SQLException {
        if (values != null) {
            Array array = stmt.getConnection().createArrayOf(type, values.toArray(factory));
            try {
                stmt.setArray(index, array);
            } finally {
                array.free();
            }
        } else {
            stmt.setNull(index, Types.ARRAY);
        }
    }
}
