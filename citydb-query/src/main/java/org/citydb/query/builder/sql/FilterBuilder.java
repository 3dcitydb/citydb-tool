/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
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

import org.citydb.database.metadata.SpatialReference;
import org.citydb.query.builder.QueryBuildException;
import org.citydb.query.builder.common.Type;
import org.citydb.query.filter.Filter;
import org.citydb.query.filter.common.*;
import org.citydb.query.filter.function.Function;
import org.citydb.query.filter.literal.*;
import org.citydb.query.filter.operation.*;
import org.citydb.sqlbuilder.literal.Placeholder;
import org.citydb.sqlbuilder.operation.BooleanExpression;
import org.citydb.sqlbuilder.query.Select;

import java.sql.Date;
import java.sql.Timestamp;

public class FilterBuilder {
    private final BuilderHelper helper;
    private final ComparisonPredicateBuilder comparisonPredicateBuilder;
    private final SpatialPredicateBuilder spatialPredicateBuilder;
    private final LogicalPredicateBuilder logicalPredicateBuilder;
    private final SqlExpressionBuilder sqlExpressionBuilder;
    private final ArithmeticExpressionBuilder arithmeticExpressionBuilder;
    private final FunctionBuilder functionBuilder;

    private FilterBuilder(BuilderHelper helper) {
        this.helper = helper;
        comparisonPredicateBuilder = ComparisonPredicateBuilder.of(this, helper);
        spatialPredicateBuilder = SpatialPredicateBuilder.of(this, helper);
        logicalPredicateBuilder = LogicalPredicateBuilder.of(this);
        sqlExpressionBuilder = SqlExpressionBuilder.newInstance();
        arithmeticExpressionBuilder = ArithmeticExpressionBuilder.of(this, helper);
        functionBuilder = FunctionBuilder.of(this, helper);
    }

    static FilterBuilder of(BuilderHelper helper) {
        return new FilterBuilder(helper);
    }

    void build(Filter filter, SpatialReference filterSrs, Select select, SqlContext context) throws QueryBuildException {
        helper.getContextBuilder().useLeftJoins(requiresLeftJoins(filter));
        spatialPredicateBuilder.setFilterSrs(filterSrs);
        select.where(build(filter.getExpression(), select, context));
    }

    BooleanExpression build(org.citydb.query.filter.operation.BooleanExpression expression, Select select, SqlContext context) throws QueryBuildException {
        try {
            return build(expression, select, context, false).build();
        } catch (Exception e) {
            throw new QueryBuildException("Failed to map the query filter to an SQL condition.", e);
        }
    }

    BuildResult build(Expression expression, Select select, SqlContext context, boolean negate) throws QueryBuildException {
        return build(expression, select, context, negate, true);
    }

    BuildResult build(Expression expression, Select select, SqlContext context, boolean negate, boolean resolveValue) throws QueryBuildException {
        if (expression instanceof ComparisonPredicate comparisonPredicate) {
            return comparisonPredicateBuilder.build(comparisonPredicate, select, context, negate);
        } else if (expression instanceof SpatialPredicate spatialPredicate) {
            return spatialPredicateBuilder.build(spatialPredicate, select, context, negate);
        } else if (expression instanceof LogicalPredicate logicalPredicate) {
            return logicalPredicateBuilder.build(logicalPredicate, select, context, negate);
        } else if (expression instanceof SqlExpression sqlExpression) {
            return sqlExpressionBuilder.build(sqlExpression, context, negate);
        } else if (expression instanceof ArithmeticExpression arithmeticExpression) {
            return arithmeticExpressionBuilder.build(arithmeticExpression, select, context, negate);
        } else if (expression instanceof Function function) {
            return functionBuilder.build(function, select, context, negate);
        } else if (expression instanceof Literal<?> literal) {
            return build(literal, negate);
        } else if (expression instanceof PropertyRef propertyRef) {
            BuildResult result = BuildResult.of(propertyRef);
            return resolveValue ?
                    result.update(helper.getContextBuilder().build(propertyRef, select, context, true)) :
                    result;
        } else {
            throw new QueryBuildException("Unsupported filter expression.");
        }
    }

    private BuildResult build(Literal<?> literal, boolean negate) throws QueryBuildException {
        if (literal instanceof org.citydb.query.filter.literal.BooleanLiteral booleanLiteral) {
            return BuildResult.of(Placeholder.of(booleanLiteral.getValue() ^ negate), Type.BOOLEAN);
        } else if (literal instanceof NumericLiteral numericLiteral) {
            return numericLiteral.isInteger() ?
                    BuildResult.of(Placeholder.of(numericLiteral.intValue()), Type.INTEGER) :
                    BuildResult.of(Placeholder.of(numericLiteral.doubleValue()), Type.DOUBLE);
        } else if (literal instanceof StringLiteral stringLiteral) {
            return BuildResult.of(Placeholder.of(stringLiteral.getValue()), Type.STRING);
        } else if (literal instanceof TimestampLiteral timestampLiteral) {
            return BuildResult.of(Placeholder.of(
                    Timestamp.from(timestampLiteral.getValue().toInstant())), Type.TIMESTAMP);
        } else if (literal instanceof DateLiteral dateLiteral) {
            return BuildResult.of(Placeholder.of(Date.valueOf(dateLiteral.getValue())), Type.DATE);
        } else if (literal instanceof BBoxLiteral bboxLiteral) {
            return BuildResult.of(Placeholder.of(bboxLiteral.getValue()), Type.ENVELOPE);
        } else if (literal instanceof GeometryLiteral geometryLiteral) {
            return BuildResult.of(Placeholder.of(geometryLiteral.getValue()), Type.GEOMETRY);
        } else {
            throw new QueryBuildException("Unsupported literal value '" +
                    literal.getValue().getClass().getSimpleName() + "'.");
        }
    }

    private boolean requiresLeftJoins(Filter filter) {
        boolean[] requiresLeftJoins = new boolean[1];
        filter.getExpression().accept(new FilterWalker() {
            @Override
            public void visit(Expression expression) {
                if (!requiresLeftJoins[0]) {
                    if (expression instanceof BinaryBooleanPredicate predicate) {
                        requiresLeftJoins[0] = predicate.getOperator() == BooleanOperator.OR;
                    } else if (expression instanceof IsNull) {
                        requiresLeftJoins[0] = true;
                    }

                    super.visit(expression);
                }
            }
        });

        return requiresLeftJoins[0];
    }
}
