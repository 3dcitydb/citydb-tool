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

package org.citydb.query.builder.sql;

import org.citydb.config.common.CountLimit;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.schema.FeatureType;
import org.citydb.database.srs.SpatialReference;
import org.citydb.database.srs.SrsException;
import org.citydb.query.Query;
import org.citydb.query.builder.QueryBuildException;
import org.citydb.query.filter.Filter;
import org.citydb.query.lod.LodFilter;
import org.citydb.query.sorting.Sorting;
import org.citydb.sqlbuilder.function.Function;
import org.citydb.sqlbuilder.function.WindowFunction;
import org.citydb.sqlbuilder.literal.IntegerLiteral;
import org.citydb.sqlbuilder.operation.Operators;
import org.citydb.sqlbuilder.query.OrderBy;
import org.citydb.sqlbuilder.query.Select;
import org.citydb.sqlbuilder.query.Window;
import org.citydb.sqlbuilder.schema.Column;
import org.citydb.sqlbuilder.schema.Table;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

public class SqlQueryBuilder {
    private final BuilderHelper helper;

    private SqlQueryBuilder(DatabaseAdapter adapter) {
        helper = BuilderHelper.of(adapter);
    }

    public static SqlQueryBuilder of(DatabaseAdapter adapter) {
        return new SqlQueryBuilder(adapter);
    }

    public Select build(Query query) throws QueryBuildException {
        return build(query, SqlBuildOptions.defaults());
    }

    public Select build(Query query, SqlBuildOptions options) throws QueryBuildException {
        Set<FeatureType> featureTypes = helper.getFeatureTypes(query);

        FeatureType featureType = helper.getSchemaMapping().getSuperType(featureTypes);
        SqlContext context = SqlContext.of(featureType, helper);
        Select select = Select.newInstance()
                .select(context.getTable().columns("id", "objectclass_id"))
                .select(context.getTable().columns(List.copyOf(options.getColumns())))
                .from(context.getTable());

        helper.getFeatureTypesBuilder().build(featureTypes, select, context);

        Filter filter = query.getFilter().orElse(null);
        if (filter != null) {
            try {
                SpatialReference filterSrs = helper.getSpatialReference(query.getFilterSrs().orElse(null));
                helper.getFilterBuilder().build(filter, filterSrs, select, context);
            } catch (SrsException | SQLException e) {
                throw new QueryBuildException("The requested filter SRS is not supported.", e);
            }
        }

        LodFilter lodFilter = query.getLodFilter().orElse(null);
        if (lodFilter != null) {
            LodFilterBuilder.of(helper).build(lodFilter, select, context);
        }

        Sorting sorting = query.getSorting().orElse(null);
        if (sorting != null) {
            SortingBuilder.of(helper).build(sorting, select, context);
        }

        CountLimit countLimit = query.getCountLimit().orElse(null);
        if (countLimit != null) {
            CountLimitBuilder.newInstance().build(countLimit, select, context);
        }

        if (!options.isOmitDistinct() && !select.getJoins().isEmpty()) {
            select = buildDistinct(query, select, context, options);
        }

        return select;
    }

    private Select buildDistinct(Query query, Select select, SqlContext context, SqlBuildOptions options) {
        if (query.getSorting().isPresent() || options.getColumns().contains("envelope")) {
            Table table = Table.of(select);
            Select outerQuery = Select.newInstance().from(table);

            select.getSelect().stream()
                    .filter(Column.class::isInstance)
                    .map(Column.class::cast)
                    .forEach(column -> outerQuery.select(table.column(column.getName())));

            select.select(WindowFunction.of(Function.of("row_number"), Window.empty()
                            .partitionBy(context.getTable().column("id"))
                            .orderBy(select.getOrderBy().toArray(OrderBy[]::new)))
                    .as("index"));
            outerQuery.where(Operators.eq(table.column("index"), IntegerLiteral.of(1)));

            for (int i = 0; i < select.getOrderBy().size(); i++) {
                select.select(select.getOrderBy().get(i).getSortExpression().as("order" + i));
                outerQuery.orderBy(OrderBy.of(table.column("order" + i), select.getOrderBy().get(i).getSortOrder()));
            }

            if (query.getCountLimit().isEmpty()) {
                select.removeOrderBy();
            }

            return outerQuery;
        } else {
            return select.distinct(true);
        }
    }
}
