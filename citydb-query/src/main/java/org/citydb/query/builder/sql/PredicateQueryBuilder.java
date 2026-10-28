/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.builder.sql;

import org.citydb.query.builder.QueryBuildException;
import org.citydb.query.filter.common.ComparisonPredicate;
import org.citydb.query.filter.common.Predicate;
import org.citydb.query.filter.common.SpatialPredicate;
import org.citydb.query.filter.operation.*;
import org.citydb.sqlbuilder.common.SqlWalker;
import org.citydb.sqlbuilder.join.Join;
import org.citydb.sqlbuilder.literal.IntegerLiteral;
import org.citydb.sqlbuilder.operation.BooleanExpression;
import org.citydb.sqlbuilder.operation.Operators;
import org.citydb.sqlbuilder.query.Select;
import org.citydb.sqlbuilder.schema.Column;

import java.util.ArrayList;
import java.util.List;

public class PredicateQueryBuilder {
    private enum Values {ALL, MATCHING, MATCHING_OR_NULL}

    private final ComparisonPredicateBuilder comparisonPredicateBuilder;
    private final SpatialPredicateBuilder spatialPredicateBuilder;

    private PredicateQueryBuilder(ComparisonPredicateBuilder comparisonPredicateBuilder, SpatialPredicateBuilder spatialPredicateBuilder) {
        this.comparisonPredicateBuilder = comparisonPredicateBuilder;
        this.spatialPredicateBuilder = spatialPredicateBuilder;
    }

    static PredicateQueryBuilder of(ComparisonPredicateBuilder comparisonPredicateBuilder, SpatialPredicateBuilder spatialPredicateBuilder) {
        return new PredicateQueryBuilder(comparisonPredicateBuilder, spatialPredicateBuilder);
    }

    BuildResult build(Predicate predicate, SqlContext context, boolean negate) throws QueryBuildException {
        Select query = Select.newInstance().select(IntegerLiteral.of(1));
        BuildResult result = build(predicate, query, context, negate);

        // single-valued: the predicate only uses scalar columns of the row in scope
        if (query.getJoins().isEmpty()) {
            return result;
        }

        // multi-valued: the predicate either asks whether some value matches (e.g. p = x)
        // or whether no value matches (e.g. NOT p = x, p NOT IN (...), p IS NULL)
        boolean someValueMatches = negate == isInverted(predicate);
        if (someValueMatches) {
            Select matchingValues = toSubQuery(query, result, Values.MATCHING);
            return BuildResult.of(Operators.exists(matchingValues));
        }

        // build the positive form, e.g. p = x for NOT p = x, or p IS NOT NULL for p IS NULL
        Select positiveQuery = Select.newInstance().select(IntegerLiteral.of(1));
        BuildResult positive = build(predicate, positiveQuery, context, !negate);

        if (predicate instanceof IsNull) {
            // p IS NULL: no non-null value exists
            Select nonNullValues = toSubQuery(positiveQuery, positive, Values.MATCHING);
            return BuildResult.of(Operators.notExists(nonNullValues));
        }

        // values exist, and none matches the positive form or is null
        Select values = toSubQuery(query, result, Values.ALL);
        Select matchingOrNullValues = toSubQuery(positiveQuery, positive, Values.MATCHING_OR_NULL);
        return BuildResult.of(Operators.and(
                Operators.exists(values),
                Operators.notExists(matchingOrNullValues)));
    }

    private BuildResult build(Predicate predicate, Select select, SqlContext context, boolean negate) throws QueryBuildException {
        SqlContext scope = SqlContext.isolatedOf(context);
        if (predicate instanceof ComparisonPredicate comparisonPredicate) {
            return comparisonPredicateBuilder.build(comparisonPredicate, select, scope, negate);
        } else if (predicate instanceof SpatialPredicate spatialPredicate) {
            return spatialPredicateBuilder.build(spatialPredicate, select, scope, negate);
        } else {
            throw new QueryBuildException("Unsupported predicate.");
        }
    }

    private Select toSubQuery(Select query, BuildResult result, Values values) throws QueryBuildException {
        // the first join becomes the FROM table, its join condition links the subquery to the outer row
        List<Join> joins = new ArrayList<>(query.getJoins());
        Join first = joins.remove(0);
        query.removeJoins()
                .from(first.getTable())
                .where(first.getConditions().toArray(BooleanExpression[]::new));
        joins.forEach(query::join);
        result.getAndResetPredicates().forEach(query::where);

        // IS [NOT] NULL on a feature relation has no condition, the join itself is the test
        if (!result.isSetExpression()) {
            return query;
        }

        return switch (values) {
            case ALL -> query;
            case MATCHING -> query.where(getCondition(result));
            case MATCHING_OR_NULL -> query.where(getConditionOrNull(result));
        };
    }

    private BooleanExpression getCondition(BuildResult result) throws QueryBuildException {
        if (result.getExpression() instanceof BooleanExpression condition) {
            return condition;
        }

        throw new QueryBuildException("Failed to build predicate as boolean expression.");
    }

    private BooleanExpression getConditionOrNull(BuildResult result) throws QueryBuildException {
        List<BooleanExpression> conditions = new ArrayList<>();
        conditions.add(getCondition(result));
        result.getOperands().stream()
                .filter(this::containsColumn)
                .map(Operators::isNull)
                .forEach(conditions::add);

        return conditions.size() == 1 ? conditions.get(0) : Operators.or(conditions);
    }

    private boolean isInverted(Predicate predicate) {
        if (predicate instanceof In in) {
            return in.getOperator() == ComparisonOperator.NOT_IN;
        } else if (predicate instanceof Like like) {
            return like.getOperator() == ComparisonOperator.NOT_LIKE;
        } else if (predicate instanceof Between between) {
            return between.getOperator() == ComparisonOperator.NOT_BETWEEN;
        } else if (predicate instanceof IsNull isNull) {
            return isNull.getOperator() == ComparisonOperator.IS_NULL;
        } else {
            return false;
        }
    }

    private boolean containsColumn(org.citydb.sqlbuilder.common.Expression expression) {
        boolean[] containsColumn = new boolean[1];
        expression.accept(new SqlWalker() {
            @Override
            public void visit(Column column) {
                containsColumn[0] = true;
            }
        });
        return containsColumn[0];
    }
}
