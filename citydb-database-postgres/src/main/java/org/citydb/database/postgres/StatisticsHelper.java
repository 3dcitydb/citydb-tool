package org.citydb.database.postgres;

import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.metadata.DatabaseSize;
import org.citydb.sqlbuilder.function.Function;
import org.citydb.sqlbuilder.literal.StringLiteral;
import org.citydb.sqlbuilder.query.Select;
import org.citydb.sqlbuilder.query.SetOperator;
import org.citydb.sqlbuilder.query.Sets;
import org.citydb.sqlbuilder.schema.Column;
import org.citydb.sqlbuilder.schema.Table;
import org.citydb.sqlbuilder.util.PlainText;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.EnumMap;
import java.util.Map;

public class StatisticsHelper extends org.citydb.database.util.StatisticsHelper {

    StatisticsHelper(DatabaseAdapter adapter) {
        super(adapter);
    }

    @Override
    public DatabaseSize getDatabaseSize(Connection connection) throws SQLException {
        String databaseSizeId = "database_size";
        Table pgTables = Table.of("pg_tables");

        SetOperator select = Sets.unionAll(
                Select.newInstance()
                        .select(StringLiteral.of(databaseSizeId), Function.of("pg_database_size",
                                StringLiteral.of(adapter.getConnectionDetails().getDatabase()))),
                Select.newInstance()
                        .select(pgTables.column("tablename"), Function.of("pg_total_relation_size",
                                PlainText.of("schemaname || '.' || tablename")))
                        .from(pgTables)
                        .where(pgTables.column("schemaname").eq(adapter.getConnectionDetails().getSchema())));

        long databaseSize = 0;
        Map<org.citydb.database.schema.Table, Long> tableSizes = new EnumMap<>(org.citydb.database.schema.Table.class);
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(select.toSql())) {
            while (rs.next()) {
                String identifier = rs.getString(1);
                if (databaseSizeId.equals(identifier)) {
                    databaseSize = rs.getLong(2);
                } else {
                    tableSizes.put(org.citydb.database.schema.Table.of(identifier), rs.getLong(2));
                }
            }
        }

        return DatabaseSize.of(databaseSize, tableSizes);
    }

    @Override
    protected Column getGeometryType(Table geometryData) {
        return geometryData.column("geometry_properties->>'type'");
    }
}
