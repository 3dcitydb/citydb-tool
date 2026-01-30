/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
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

package org.citydb.database.postgres;

import org.citydb.core.tuple.Pair;
import org.citydb.core.version.Version;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.sqlbuilder.function.Function;
import org.citydb.sqlbuilder.literal.IntegerLiteral;
import org.citydb.sqlbuilder.query.CommonTableExpression;
import org.citydb.sqlbuilder.query.Select;
import org.citydb.sqlbuilder.query.Selection;
import org.citydb.sqlbuilder.schema.Table;
import org.citydb.sqlbuilder.util.PlainSql;

public class ChangelogHelper implements org.citydb.database.util.ChangelogHelper {
    private final PostgresqlAdapter adapter;

    ChangelogHelper(DatabaseAdapter adapter) {
        this.adapter = (PostgresqlAdapter) adapter;
    }

    @Override
    public Select getChangeRegions(Select baseQuery, Selection<?> envelope) {
        return getAndTransformChangeRegions(baseQuery, envelope,
                adapter.getDatabaseMetadata().getSpatialReference().getSRID());
    }

    @Override
    public Select getAndTransformChangeRegions(Select baseQuery, Selection<?> envelope, int srid) {
        String alias = "extent";
        envelope.as(alias);
        Table base = Table.of(baseQuery);

        CommonTableExpression extents = CommonTableExpression.of("extents", Select.newInstance()
                .select(Function.of("st_force2d", base.column(alias)).as(alias))
                .from(base)
                .where(base.column(alias).isNotNull()));
        Pair<Table, Select> result = adapter.getPostGISVersion().compareTo(Version.of(3, 4, 0)) >= 0 ?
                buildWithWindowFunction(extents, alias) :
                buildWithoutWindowFunction(extents, alias);

        Selection<?> cluster = Function.of("st_union", result.first().column(alias));
        if (adapter.getDatabaseMetadata().getSpatialReference().getSRID() != srid) {
            cluster = Function.of("st_transform", cluster, IntegerLiteral.of(srid));
        }

        return result.second()
                .select(PlainSql.of("({}).geom", Function.of("st_dump", cluster)).as("region"));
    }

    private Pair<Table, Select> buildWithWindowFunction(CommonTableExpression extents, String alias) {
        CommonTableExpression clusters = CommonTableExpression.of("clusters", Select.newInstance()
                .select(extents.asTable().column(alias).as(alias))
                .select(Function.of("st_clusterintersectingwin", extents.asTable().column(alias)).over()
                        .as("cluster_id"))
                .from(extents.asTable()));

        return Pair.of(clusters.asTable(), Select.newInstance()
                .with(extents)
                .with(clusters)
                .from(clusters.asTable())
                .groupBy(clusters.asTable().column("cluster_id")));
    }

    private Pair<Table, Select> buildWithoutWindowFunction(CommonTableExpression extents, String alias) {
        CommonTableExpression clustered = CommonTableExpression.of("clustered_extents", Select.newInstance()
                .select(Function.of("st_clusterintersecting", extents.asTable().column(alias)).as("cluster"))
                .from(extents.asTable()));
        CommonTableExpression clusters = CommonTableExpression.of("clusters", Select.newInstance()
                .select(Function.of("unnest", clustered.asTable().column("cluster")).as(alias))
                .select(Function.of("generate_subscripts", clustered.asTable().column("cluster"), IntegerLiteral.of(1))
                        .as("cluster_id"))
                .from(clustered.asTable()));

        return Pair.of(clusters.asTable(), Select.newInstance()
                .with(extents)
                .with(clustered)
                .with(clusters)
                .from(clusters.asTable())
                .groupBy(clusters.asTable().column("cluster_id")));
    }
}
