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

package org.citydb.util.changelog.query;

import org.citydb.core.tuple.Pair;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.schema.FeatureType;
import org.citydb.database.srs.SpatialReference;
import org.citydb.database.util.SpatialOperationHelper;
import org.citydb.database.util.SqlExpressionValidator;
import org.citydb.model.common.PrefixedName;
import org.citydb.model.geometry.Envelope;
import org.citydb.sqlbuilder.literal.*;
import org.citydb.sqlbuilder.operation.BooleanExpression;
import org.citydb.sqlbuilder.operation.In;
import org.citydb.sqlbuilder.operation.Operators;
import org.citydb.sqlbuilder.query.OrderBy;
import org.citydb.sqlbuilder.query.Select;
import org.citydb.sqlbuilder.schema.Column;
import org.citydb.sqlbuilder.schema.Table;
import org.citydb.sqlbuilder.util.PlainSql;
import org.citydb.util.changelog.ChangelogHelper;
import org.citydb.util.changelog.options.BboxMode;
import org.citydb.util.changelog.options.SortOrder;
import org.citydb.util.changelog.options.TransactionDate;

import java.util.*;
import java.util.function.Function;

public class QueryBuilder {
    private final DatabaseAdapter adapter;
    private final ChangelogHelper helper;
    private final SpatialOperationHelper spatialOperationHelper;
    private final SpatialReference databaseSrs;

    private QueryBuilder(DatabaseAdapter adapter, ChangelogHelper helper) {
        this.adapter = Objects.requireNonNull(adapter, "The database adapter must not be null.");
        this.helper = Objects.requireNonNull(helper, "The changelog helper must not be null.");
        spatialOperationHelper = adapter.getGeometryAdapter().getSpatialOperationHelper();
        databaseSrs = adapter.getDatabaseMetadata().getSpatialReference();
    }

    public static QueryBuilder of(DatabaseAdapter adapter, ChangelogHelper helper) {
        return new QueryBuilder(adapter, helper);
    }

    public Select buildForChanges(ChangelogQuery query) throws QueryBuildException {
        SpatialReference targetSrs = helper.getTargetSrs(query);
        Pair<Table, Select> result = build(query);
        Column envelope = result.first().column("envelope");

        query.getSortOrder().ifPresent(sortOrder ->
                result.second().orderBy(buildOrderBy(sortOrder, result.first().column("transaction_date"))));

        return result.second()
                .select(result.first().columns("id", "feature_id", "objectclass_id", "objectid",
                        "identifier", "identifier_codespace", "transaction_type", "transaction_date",
                        "db_user", "reason_for_update"))
                .select(databaseSrs.getSRID() == targetSrs.getSRID() ?
                        envelope :
                        spatialOperationHelper.transform(envelope, targetSrs.getSRID()).as("envelope"));
    }

    public Select buildForRegions(ChangelogQuery query) throws QueryBuildException {
        SpatialReference targetSrs = helper.getTargetSrs(query);
        Pair<Table, Select> result = build(query);
        Column envelope = result.first().column("envelope");
        Select select = result.second().select(envelope);

        org.citydb.database.util.ChangelogHelper changelogHelper = adapter.getSchemaAdapter().getChangelogHelper();
        return databaseSrs.getSRID() == targetSrs.getSRID() ?
                changelogHelper.getChangeRegions(select, envelope) :
                changelogHelper.getAndTransformChangeRegions(select, envelope, targetSrs.getSRID());
    }

    private Pair<Table, Select> build(ChangelogQuery query) throws QueryBuildException {
        Table table = Table.of(org.citydb.database.schema.Table.FEATURE_CHANGELOG.getName(),
                adapter.getConnectionDetails().getSchema());
        Select select = Select.newInstance().from(table);

        query.getBbox().ifPresent(bbox ->
                select.where(buildBboxFilter(bbox, query.getBboxMode(), table.column("envelope"))));
        buildFeatureTypesFilter(query.getFeatureTypes(), table.column("objectclass_id"))
                .ifPresent(select::where);
        buildComparisonOperator(query.getTransactionTypes(), type -> StringLiteral.of(type.getDatabaseValue()),
                table.column("transaction_type"))
                .ifPresent(select::where);
        query.getTransactionDate()
                .map(date -> buildTransactionDateFilter(date, table.column("transaction_date")))
                .ifPresent(select::where);
        buildComparisonOperator(query.getIds(), StringLiteral::of, table.column("objectid"))
                .ifPresent(select::where);
        buildComparisonOperator(query.getDatabaseUsers(), StringLiteral::of, table.column("db_user"))
                .ifPresent(select::where);
        buildComparisonOperator(query.getReasonsForUpdate(), StringLiteral::of, table.column("reason_for_update"))
                .ifPresent(select::where);

        String sqlFilter = query.getSqlFilter().orElse(null);
        if (sqlFilter != null) {
            select.where(buildSqlFilter(sqlFilter, table.column("id")));
        }

        return Pair.of(table, select);
    }

    private BooleanExpression buildBboxFilter(Envelope bbox, BboxMode mode, Column column) {
        ScalarExpression spatialObject = Placeholder.of(bbox);
        if (databaseSrs.getSRID() != bbox.getSRID().orElse(databaseSrs.getSRID())) {
            spatialObject = spatialOperationHelper.transform(spatialObject, databaseSrs.getSRID());
        }

        return switch (mode) {
            case INTERSECTS -> spatialOperationHelper.bbox(column, spatialObject);
            case CONTAINS -> spatialOperationHelper.contains(column, spatialObject);
        };
    }

    private Optional<BooleanExpression> buildFeatureTypesFilter(List<PrefixedName> featureTypes, Column column) throws QueryBuildException {
        Set<Integer> ids = new HashSet<>();
        for (PrefixedName name : featureTypes) {
            FeatureType featureType = adapter.getSchemaAdapter().getSchemaMapping().getFeatureType(name, true);
            if (featureType == FeatureType.UNDEFINED) {
                throw new QueryBuildException("The feature type '" + name + "' is undefined.");
            } else {
                ids.add(featureType.getId());
            }
        }

        return buildComparisonOperator(ids, IntegerLiteral::of, column);
    }

    private BooleanExpression buildTransactionDateFilter(TransactionDate transactionDate, Column column) {
        List<BooleanExpression> expressions = new ArrayList<>();
        transactionDate.getAfter().ifPresent(after -> expressions.add(column.gt(TimestampLiteral.of(after))));
        transactionDate.getUntil().ifPresent(until -> expressions.add(column.le(TimestampLiteral.of(until))));

        return !expressions.isEmpty() ? Operators.and(expressions) : null;
    }

    private BooleanExpression buildSqlFilter(String sqlFilter, Column column) throws QueryBuildException {
        SqlExpressionValidator.defaults().validate(sqlFilter, invalid ->
                new QueryBuildException("Found illegal content in SQL expression: " + invalid));

        return In.of(column, PlainSql.of(sqlFilter));
    }

    private <T> Optional<BooleanExpression> buildComparisonOperator(Collection<T> values, Function<T, ScalarExpression> literal, Column column) {
        return switch (values.size()) {
            case 0 -> Optional.empty();
            case 1 -> Optional.of(Operators.eq(column, literal.apply(values.iterator().next())));
            default -> Optional.of(In.of(column, values.stream()
                    .map(literal)
                    .toList()));
        };
    }

    private OrderBy buildOrderBy(SortOrder sortOrder, Column column) {
        return switch (sortOrder) {
            case LATEST_FIRST -> OrderBy.of(column, OrderBy.DESCENDING);
            case OLDEST_FIRST -> OrderBy.of(column, OrderBy.ASCENDING);
        };
    }
}
