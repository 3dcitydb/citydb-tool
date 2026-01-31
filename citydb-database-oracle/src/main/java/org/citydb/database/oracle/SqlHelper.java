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

import org.citydb.database.adapter.DatabaseAdapter;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;

public class SqlHelper extends org.citydb.database.util.SqlHelper {

    SqlHelper(DatabaseAdapter adapter) {
        super(adapter);
    }

    @Override
    public void setBytesOrNull(PreparedStatement stmt, int index, byte[] bytes) throws SQLException {

    }

    @Override
    public void setJsonOrNull(PreparedStatement stmt, int index, String json) throws SQLException {

    }

    @Override
    public void setGeometryOrNull(PreparedStatement stmt, int index, Object geometry) throws SQLException {

    }

    @Override
    public void setLongArrayOrNull(PreparedStatement stmt, int index, Collection<Long> values) throws SQLException {

    }

    @Override
    public void setStringArrayOrNull(PreparedStatement stmt, int index, Collection<String> values) throws SQLException {

    }
}
