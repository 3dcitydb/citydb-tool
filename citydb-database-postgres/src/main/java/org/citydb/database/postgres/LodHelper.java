/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.postgres;

import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.util.LodStatistics;
import org.citydb.model.property.RelationType;
import org.citydb.sqlbuilder.literal.IntegerLiteral;
import org.citydb.sqlbuilder.literal.StringLiteral;
import org.citydb.sqlbuilder.operation.BooleanExpression;
import org.citydb.sqlbuilder.query.CommonTableExpression;
import org.citydb.sqlbuilder.query.Select;
import org.citydb.sqlbuilder.schema.Table;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class LodHelper extends org.citydb.database.util.LodHelper {

    protected LodHelper(DatabaseAdapter adapter) {
        super(adapter);
    }

    @Override
    public Select getBottomUpQuery(Set<String> lods) {
        Table hierarchy = Table.of("lod_hierarchy");
        Table property = Table.of(org.citydb.database.schema.Table.PROPERTY.getName(),
                adapter.getConnectionDetails().getSchema());
        Table parent = Table.of(org.citydb.database.schema.Table.PROPERTY.getName(),
                adapter.getConnectionDetails().getSchema());

        Select geometryQuery = Select.newInstance()
                .select(property.column("feature_id").as("id"))
                .from(property)
                .where(property.column("val_geometry_id").isNotNull()
                                .or(property.column("val_implicitgeom_id").isNotNull()),
                        getLodCondition(property, lods));
        Select parentQuery = Select.newInstance()
                .select(parent.column("feature_id"))
                .from(parent)
                .join(hierarchy).on(hierarchy.column("id").eq(parent.column("val_feature_id")))
                .where(parent.column("val_relation_type").eq(RelationType.CONTAINS.getDatabaseValue()));

        CommonTableExpression cte = CommonTableExpression.of(hierarchy.getName(),
                geometryQuery.union(parentQuery));
        return Select.newInstance()
                .withRecursive(cte)
                .select(cte.asTable().column("id"))
                .from(cte.asTable());
    }

    @Override
    public Select getTopDownQuery(Set<String> lods, Table table) {
        Table hierarchy = Table.of("hierarchy");
        Table property = Table.of(org.citydb.database.schema.Table.PROPERTY.getName(),
                adapter.getConnectionDetails().getSchema());

        Select featureQuery = Select.newInstance()
                .select(table.column("id").as("val_feature_id"));
        Select propertyQuery = Select.newInstance()
                .select(property.column("val_feature_id"))
                .from(property)
                .join(hierarchy).on(hierarchy.column("val_feature_id").eq(property.column("feature_id")))
                .where(property.column("val_feature_id").isNotNull(),
                        property.column("val_relation_type").eq(RelationType.CONTAINS.getDatabaseValue()));

        CommonTableExpression cte = CommonTableExpression.of(hierarchy.getName(),
                featureQuery.union(propertyQuery));
        return Select.newInstance()
                .withRecursive(cte)
                .select(IntegerLiteral.of(1))
                .from(property)
                .join(cte.asTable()).on(cte.asTable().column("val_feature_id").eq(property.column("feature_id")))
                .where(property.column("val_geometry_id").isNotNull()
                                .or(property.column("val_implicitgeom_id").isNotNull()),
                        getLodCondition(property, lods))
                .fetch(1);
    }

    private BooleanExpression getLodCondition(Table property, Set<String> lods) {
        return lods.isEmpty()
                ? property.column("val_lod").isNotNull()
                : adapter.getSchemaAdapter().getOperationHelper()
                .in(property.column("val_lod"), lods.stream().map(StringLiteral::of).toList());
    }

    @Override
    public long getSearchThreshold() {
        return 50000;
    }

    @Override
    public Optional<LodStatistics> getStatistics(Connection connection) throws SQLException {
        // most common values with their frequencies from the planner statistics, together with the number of
        // rows at the time of the analysis and the number of rows modified since then
        String sql = "select s.most_common_vals::text::text[], s.most_common_freqs, s.null_frac, c.reltuples, " +
                "coalesce(t.n_mod_since_analyze, 0) " +
                "from pg_class c " +
                "join pg_namespace n on n.oid = c.relnamespace " +
                "join pg_stats s on s.schemaname = n.nspname and s.tablename = c.relname and s.attname = 'val_lod' " +
                "left join pg_stat_user_tables t on t.relid = c.oid " +
                "where n.nspname = ? and c.relname = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, adapter.getConnectionDetails().getSchema());
            stmt.setString(2, org.citydb.database.schema.Table.PROPERTY.getName());
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next() || rs.getFloat(4) < 0) {
                    // no statistics, e.g. because the table has not been analyzed yet
                    return Optional.empty();
                }

                Map<String, Double> frequencies = new HashMap<>();
                Array values = rs.getArray(1);
                Array freqs = rs.getArray(2);
                if (values != null && freqs != null) {
                    String[] lods = (String[]) values.getArray();
                    Float[] shares = (Float[]) freqs.getArray();
                    for (int i = 0; i < lods.length && i < shares.length; i++) {
                        frequencies.put(lods[i], shares[i].doubleValue());
                    }
                }

                double nullFraction = rs.getFloat(3);
                if (frequencies.isEmpty() && nullFraction < 1) {
                    // the LoDs are not listed, so the statistics do not tell how often they occur
                    return Optional.empty();
                }

                // the statistics are stale if more than 10% of the rows were modified since the analysis, which
                // is also the default share at which autovacuum analyzes a table again
                long rows = (long) rs.getFloat(4);
                return Optional.of(LodStatistics.of(frequencies, nullFraction, rows, rs.getLong(5) > 0.1 * rows));
            }
        }
    }
}
