/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.builder.sql;

import org.citydb.database.srs.SpatialReference;
import org.citydb.model.geometry.GeometryCollection;
import org.citydb.query.builder.QueryBuildException;
import org.citydb.query.builder.common.Type;
import org.citydb.query.filter.Filter;
import org.citydb.query.filter.common.*;
import org.citydb.query.filter.function.Function;
import org.citydb.query.filter.literal.*;
import org.citydb.query.filter.operation.ArithmeticExpression;
import org.citydb.query.filter.operation.NumericExpression;
import org.citydb.query.filter.operation.SqlExpression;
import org.citydb.sqlbuilder.join.Joins;
import org.citydb.sqlbuilder.literal.Placeholder;
import org.citydb.sqlbuilder.operation.BooleanExpression;
import org.citydb.sqlbuilder.query.Select;

import java.sql.Date;
import java.sql.Timestamp;

public class FilterBuilder {
    private final BuilderHelper helper;
    private final SpatialPredicateBuilder spatialPredicateBuilder;
    private final LogicalPredicateBuilder logicalPredicateBuilder;
    private final SqlExpressionBuilder sqlExpressionBuilder;
    private final ArithmeticExpressionBuilder arithmeticExpressionBuilder;
    private final FunctionBuilder functionBuilder;
    private final PredicateQueryBuilder predicateQueryBuilder;

    private FilterBuilder(BuilderHelper helper) {
        this.helper = helper;
        spatialPredicateBuilder = SpatialPredicateBuilder.of(this, helper);
        logicalPredicateBuilder = LogicalPredicateBuilder.of(this);
        sqlExpressionBuilder = SqlExpressionBuilder.newInstance();
        arithmeticExpressionBuilder = ArithmeticExpressionBuilder.of(this, helper);
        functionBuilder = FunctionBuilder.of(this, helper);
        predicateQueryBuilder = PredicateQueryBuilder.of(ComparisonPredicateBuilder.of(this, helper),
                spatialPredicateBuilder);
    }

    static FilterBuilder of(BuilderHelper helper) {
        return new FilterBuilder(helper);
    }

    void build(Filter filter, SpatialReference filterSrs, Select select, SqlContext context) throws QueryBuildException {
        spatialPredicateBuilder.setFilterSrs(filterSrs);
        select.where(build(filter.getExpression(), select, context));
    }

    BooleanExpression build(org.citydb.query.filter.operation.BooleanExpression expression, Select select, SqlContext context) throws QueryBuildException {
        try {
            return build(expression, select, context, false).build();
        } catch (Exception e) {
            throw new QueryBuildException("Failed to map the filter expression to an SQL condition.", e);
        }
    }

    BuildResult build(Expression expression, Select select, SqlContext context) throws QueryBuildException {
        return build(expression, select, context, false);
    }

    BuildResult build(Expression expression, Select select, SqlContext context, boolean negate) throws QueryBuildException {
        return build(expression, select, context, negate, true);
    }

    BuildResult build(Expression expression, Select select, SqlContext context, String joinType) throws QueryBuildException {
        return build(expression, select, context, joinType, false, true);
    }

    BuildResult build(Expression expression, Select select, SqlContext context, boolean negate, boolean resolveValue) throws QueryBuildException {
        return build(expression, select, context, Joins.INNER_JOIN, negate, resolveValue);
    }

    BuildResult build(Expression expression, Select select, SqlContext context, String joinType, boolean negate, boolean resolveValue) throws QueryBuildException {
        BuildResult result = switch (expression) {
            case ComparisonPredicate comparisonPredicate ->
                    predicateQueryBuilder.build(comparisonPredicate, context, negate);
            case SpatialPredicate spatialPredicate -> predicateQueryBuilder.build(spatialPredicate, context, negate);
            case LogicalPredicate logicalPredicate ->
                    logicalPredicateBuilder.build(logicalPredicate, select, context, negate);
            case SqlExpression sqlExpression -> sqlExpressionBuilder.build(sqlExpression, context, negate);
            case ArithmeticExpression arithmeticExpression ->
                    arithmeticExpressionBuilder.build(arithmeticExpression, select, context, negate);
            case Function function -> functionBuilder.build(function, select, context, negate);
            case Literal<?> literal -> build(literal, negate);
            case PropertyRef propertyRef -> {
                BuildResult propertyResult = BuildResult.of(propertyRef);
                if (resolveValue) {
                    SqlContext scope = context.isIsolated() ? SqlContext.isolatedOf(context) : context;
                    propertyResult.update(helper.getContextBuilder().build(propertyRef, select, scope, joinType, true));
                }

                yield propertyResult;
            }
            case null -> throw new QueryBuildException("Unsupported filter expression.");
        };

        if (resolveValue) {
            applySign(expression, result);
        }

        return result;
    }

    private BuildResult build(Literal<?> literal, boolean negate) throws QueryBuildException {
        return switch (literal) {
            case BooleanLiteral booleanLiteral ->
                    BuildResult.of(Placeholder.of(booleanLiteral.getValue() ^ negate), Type.BOOLEAN);
            case NumericLiteral numericLiteral -> numericLiteral.isInteger()
                    ? BuildResult.of(Placeholder.of(numericLiteral.intValue()), Type.INTEGER)
                    : BuildResult.of(Placeholder.of(numericLiteral.doubleValue()), Type.DOUBLE);
            case StringLiteral stringLiteral -> BuildResult.of(Placeholder.of(stringLiteral.getValue()), Type.STRING);
            case TimestampLiteral timestampLiteral -> BuildResult.of(Placeholder.of(
                    Timestamp.from(timestampLiteral.getValue().toInstant())), Type.TIMESTAMP);
            case DateLiteral dateLiteral ->
                    BuildResult.of(Placeholder.of(Date.valueOf(dateLiteral.getValue())), Type.DATE);
            case BBoxLiteral bboxLiteral -> BuildResult.of(Placeholder.of(bboxLiteral.getValue()), Type.ENVELOPE);
            case GeometryLiteral geometryLiteral ->
                    BuildResult.of(Placeholder.of(geometryLiteral.getValue()), Type.GEOMETRY);
            case GeometryCollectionLiteral collectionLiteral -> build(collectionLiteral);
        };
    }

    private BuildResult build(GeometryCollectionLiteral literal) {
        return BuildResult.of(Placeholder.of(GeometryCollection.of(literal.getValue().stream()
                .map(GeometryLiteral::getValue)
                .toList())), Type.GEOMETRY);
    }

    private void applySign(Expression expression, BuildResult result) {
        if (expression instanceof NumericExpression numericExpression
                && !(expression instanceof NumericLiteral)
                && numericExpression.getSign() == Sign.MINUS
                && result.isSetExpression()) {
            result.negate();
        }
    }
}
