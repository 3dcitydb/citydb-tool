/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.postgres;

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
    private final String schemaPrefix;

    SequenceHelper(Connection connection, DatabaseAdapter adapter) throws SQLException {
        super(connection);
        helper = adapter.getSchemaAdapter().getSqlHelper();
        schemaPrefix = adapter.getConnectionDetails().getSchema() + ".";

        stmt = connection.prepareStatement("select e.key, array_agg(nextval(e.key)) " +
                "from jsonb_each_text(?::jsonb) as e " +
                "join lateral generate_series(1, e.value::bigint) as g(i) on true " +
                "group by e.key");
    }

    @Override
    protected Map<Sequence, List<Long>> fetchNextValues(Map<Sequence, Integer> counts) throws SQLException {
        Map<Sequence, List<Long>> results = new EnumMap<>(Sequence.class);
        helper.setJsonOrNull(stmt, 1, getJson(counts));
        try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Sequence sequence = Sequence.of(rs.getString(1).substring(schemaPrefix.length()));
                Long[] values = (Long[]) rs.getArray(2).getArray();
                results.put(sequence, Arrays.asList(values));
            }
        }

        return results;
    }

    private String getJson(Map<Sequence, Integer> counts) {
        StringBuilder json = new StringBuilder("{");
        Iterator<Map.Entry<Sequence, Integer>> iterator = counts.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Sequence, Integer> entry = iterator.next();
            json.append('"')
                    .append(schemaPrefix)
                    .append(entry.getKey().getName())
                    .append("\":")
                    .append(entry.getValue());
            if (iterator.hasNext()) {
                json.append(',');
            }
        }

        return json.append("}").toString();
    }

    @Override
    public void close() throws SQLException {
        stmt.close();
    }
}
