/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2026
 * virtualcitysystems GmbH, Germany
 * https://vc.systems/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.citydb.database.oracle;

import oracle.jdbc.OracleConnection;
import oracle.jdbc.OracleTypes;
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
            stmt.setString(index, json);
        } else {
            stmt.setNull(index, OracleTypes.JSON);
        }
    }

    @Override
    public void setGeometryOrNull(PreparedStatement stmt, int index, Object geometry) throws SQLException {
        if (geometry != null) {
            stmt.setObject(index, geometry, OracleTypes.STRUCT);
        } else {
            stmt.setNull(index, OracleTypes.STRUCT, "MDSYS.SDO_GEOMETRY");
        }
    }

    @Override
    public void setLongArrayOrNull(PreparedStatement stmt, int index, Collection<Long> values) throws SQLException {
        setArrayOrNull(stmt, index, values, "NUMBER_TAB", Long[]::new);
    }

    @Override
    public void setStringArrayOrNull(PreparedStatement stmt, int index, Collection<String> values) throws SQLException {
        setArrayOrNull(stmt, index, values, "STRING_TAB", String[]::new);
    }

    private <T> void setArrayOrNull(PreparedStatement stmt, int index, Collection<T> values, String type, IntFunction<T[]> factory) throws SQLException {
        if (values != null) {
            OracleConnection connection = stmt.getConnection().unwrap(OracleConnection.class);
            Array array = connection.createOracleArray(type, values.toArray(factory));
            stmt.setArray(index, array);
        } else {
            stmt.setNull(index, Types.ARRAY, type);
        }
    }
}
