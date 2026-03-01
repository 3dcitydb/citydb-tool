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
import org.citydb.database.schema.Sequence;
import org.citydb.database.util.SqlHelper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class SequenceHelper extends org.citydb.database.util.SequenceHelper {
    private final SqlHelper helper;
    private final PreparedStatement stmt;
    private final int prefixLength;
    private final Map<Sequence, String> prefixedSequenceNames = new EnumMap<>(Sequence.class);

    SequenceHelper(Connection connection, DatabaseAdapter adapter) throws SQLException {
        super(connection);
        helper = adapter.getSchemaAdapter().getSqlHelper();
        String prefix = adapter.getConnectionDetails().getSchema() + ".";
        prefixLength = prefix.length();
        for (Sequence sequence : Sequence.values()) {
            prefixedSequenceNames.put(sequence, prefix + sequence.getName());
        }

        stmt = connection.prepareStatement("select " +
                "jt.name, citydb_util.get_seq_values(jt.name, jt.cnt) " +
                "from json_table(?, '$[*]' columns (" +
                "  name varchar2(128) path '$.seq_name', " +
                "  cnt  NUMBER path '$.count'" +
                ") ) jt");
    }

    @Override
    protected Map<Sequence, List<Long>> fetchNextValues(Map<Sequence, Integer> counts) throws SQLException {
        Map<Sequence, List<Long>> results = new EnumMap<>(Sequence.class);
        helper.setJsonOrNull(stmt, 1, getJson(counts));
        try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Sequence sequence = Sequence.of(rs.getString(1).substring(prefixLength));
                Number[] values = (Number[]) rs.getArray(2).getArray();
                results.put(sequence, Arrays.stream(values).map(Number::longValue).toList());
            }
        }

        return results;
    }

    private String getJson(Map<Sequence, Integer> counts) {
        StringBuilder json = new StringBuilder("[");
        Iterator<Map.Entry<Sequence, Integer>> iterator = counts.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Sequence, Integer> entry = iterator.next();
            json.append("{\"seq_name\":\"")
                    .append(prefixedSequenceNames.get(entry.getKey()))
                    .append("\", \"count\":")
                    .append(entry.getValue());
            json.append("}");
            if (iterator.hasNext()) {
                json.append(',');
            }
        }

        return json.append("]").toString();
    }

    @Override
    public void close() throws SQLException {
        stmt.close();
    }
}
