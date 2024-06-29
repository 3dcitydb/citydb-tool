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

import org.citydb.database.schema.*;
import org.citydb.query.builder.QueryBuildException;
import org.citydb.query.builder.common.Type;
import org.citydb.query.builder.schema.Node;
import org.citydb.query.builder.schema.SchemaPathException;
import org.citydb.query.filter.common.ComparisonPredicate;
import org.citydb.query.filter.common.Expression;
import org.citydb.query.filter.operation.Between;
import org.citydb.query.filter.operation.BinaryComparisonPredicate;
import org.citydb.query.filter.operation.ComparisonOperator;
import org.citydb.query.filter.operation.IsNull;
import org.citydb.sqlbuilder.common.SqlObject;
import org.citydb.sqlbuilder.common.SqlWalker;
import org.citydb.sqlbuilder.literal.Placeholder;
import org.citydb.sqlbuilder.literal.ScalarExpression;
import org.citydb.sqlbuilder.literal.StringLiteral;
import org.citydb.sqlbuilder.operation.In;
import org.citydb.sqlbuilder.operation.Like;
import org.citydb.sqlbuilder.operation.Operators;
import org.citydb.sqlbuilder.query.Select;

import java.util.ArrayList;
import java.util.List;

public class ComparisonPredicateBuilder {
    private final FilterBuilder filterBuilder;
    private final BuilderHelper helper;

    private ComparisonPredicateBuilder(FilterBuilder filterBuilder, BuilderHelper helper) {
        this.filterBuilder = filterBuilder;
        this.helper = helper;
    }

    static ComparisonPredicateBuilder of(FilterBuilder filterBuilder, BuilderHelper helper) {
        return new ComparisonPredicateBuilder(filterBuilder, helper);
    }

    BuildResult build(ComparisonPredicate predicate, Select select, SqlContext context, boolean negate) throws QueryBuildException {
        if (predicate instanceof BinaryComparisonPredicate binaryComparisonPredicate) {
            return build(binaryComparisonPredicate, select, context, negate);
        } else if (predicate instanceof Between between) {
            return build(between, select, context, negate);
        } else if (predicate instanceof org.citydb.query.filter.operation.In in) {
            return build(in, select, context, negate);
        } else if (predicate instanceof IsNull isNull) {
            return build(isNull, select, context, negate);
        } else if (predicate instanceof org.citydb.query.filter.operation.Like like) {
            return build(like, select, context, negate);
        } else {
            throw new QueryBuildException("Unsupported comparison predicate.");
        }
    }

    private BuildResult build(BinaryComparisonPredicate predicate, Select select, SqlContext context, boolean negate) throws QueryBuildException {
        BuildResult leftOperand = filterBuilder.build(predicate.getLeftOperand(), select, context, negate);
        BuildResult rightOperand = filterBuilder.build(predicate.getRightOperand(), select, context, negate);

        helper.cast(leftOperand, rightOperand);
        leftOperand.requireType(Type.SCALAR_TYPES, () -> "A value of type '" + leftOperand.getType() +
                "' cannot be used as left operand of a binary comparison predicate.");
        rightOperand.requireType(Type.SCALAR_TYPES, () -> "A value of type '" + rightOperand.getType() +
                "' cannot be used as right operand of a binary comparison predicate.");

        if (leftOperand.getType() != rightOperand.getType()) {
            throw new QueryBuildException("Type mismatch between operands of a binary comparison predicate.");
        }

        if (leftOperand.getExpression() instanceof ScalarExpression leftExpression
                && rightOperand.getExpression() instanceof ScalarExpression rightExpression) {
            return BuildResult.of(switch (predicate.getOperator()) {
                case EQUAL_TO -> Operators.eq(leftExpression, rightExpression, negate);
                case NOT_EQUAL_TO -> Operators.ne(leftExpression, rightExpression, negate);
                case LESS_THAN -> Operators.lt(leftExpression, rightExpression, negate);
                case LESS_THAN_OR_EQUAL_TO -> Operators.le(leftExpression, rightExpression, negate);
                case GREATER_THAN -> Operators.gt(leftExpression, rightExpression, negate);
                case GREATER_THAN_OR_EQUAL_TO -> Operators.ge(leftExpression, rightExpression, negate);
                default -> throw new QueryBuildException("Unsupported operator '" +
                        predicate.getOperator().getTextToken() + "' used in binary comparison predicate.");
            }, leftOperand, rightOperand);
        } else {
            throw new QueryBuildException("Failed to build binary comparison predicate.");
        }
    }

    private BuildResult build(Between between, Select select, SqlContext context, boolean negate) throws QueryBuildException {
        BuildResult operand = filterBuilder.build(between.getOperand(), select, context, negate);
        BuildResult lowerBound = filterBuilder.build(between.getLowerBound(), select, context, negate);
        BuildResult upperBound = filterBuilder.build(between.getUpperBound(), select, context, negate);

        operand.requireType(Type.NUMERIC_TYPES, () -> "A value of type '" + operand.getType() +
                "' cannot be used as operand of a between predicate.");
        lowerBound.requireType(Type.NUMERIC_TYPES, () -> "A value of type '" + lowerBound.getType() +
                "' cannot be used as lower bound of a between predicate.");
        upperBound.requireType(Type.NUMERIC_TYPES, () -> "A value of type '" + upperBound.getType() +
                "' cannot be used as upper bound of a between predicate.");

        if (operand.getExpression() instanceof ScalarExpression operandExpression
                && lowerBound.getExpression() instanceof ScalarExpression lowerBoundExpression
                && upperBound.getExpression() instanceof ScalarExpression upperBoundExpression) {
            negate = negate ^ between.getOperator() == ComparisonOperator.NOT_BETWEEN;
            return BuildResult.of(
                    Operators.between(operandExpression, lowerBoundExpression, upperBoundExpression, negate),
                    operand, lowerBound, upperBound);
        } else {
            throw new QueryBuildException("Failed to build between predicate.");
        }
    }

    private BuildResult build(org.citydb.query.filter.operation.In in, Select select, SqlContext context, boolean negate) throws QueryBuildException {
        BuildResult operand = filterBuilder.build(in.getOperand(), select, context, negate);
        List<BuildResult> values = new ArrayList<>(in.getValues().size());
        for (Expression expression : in.getValues()) {
            values.add(filterBuilder.build(expression, select, context, negate));
        }

        helper.cast(values);
        helper.cast(operand, values.get(0));

        operand.requireType(Type.SCALAR_TYPES, () -> "A value of type '" + operand.getType() +
                "' cannot be used as operand of an in predicate.");
        for (BuildResult value : values) {
            value.requireType(Type.SCALAR_TYPES, () -> "A value of type '" + value.getType() +
                    "' cannot be used as value of an in predicate.");
        }

        if (values.size() > 1 && values.stream()
                .map(BuildResult::getType)
                .anyMatch(type -> !Type.NUMERIC_TYPES.contains(type))) {
            throw new QueryBuildException("The values of an in predicate use incompatible types.");
        } else if (operand.getType() != values.get(0).getType()) {
            throw new QueryBuildException("Type mismatch between the operand and values of an in predicate.");
        }

        List<ScalarExpression> valueExpressions = values.stream()
                .map(BuildResult::getExpression)
                .filter(ScalarExpression.class::isInstance)
                .map(ScalarExpression.class::cast)
                .toList();

        if (operand.getExpression() instanceof ScalarExpression operandExpression
                && values.size() == valueExpressions.size()) {
            negate = negate ^ in.getOperator() == ComparisonOperator.NOT_IN;
            values.add(operand);
            return valueExpressions.size() == 1 ?
                    BuildResult.of(Operators.eq(operandExpression, valueExpressions.get(0), negate), values) :
                    BuildResult.of(In.of(operandExpression, valueExpressions, negate), values);
        } else {
            throw new QueryBuildException("Failed to build in predicate.");
        }
    }

    private BuildResult build(IsNull isNull, Select select, SqlContext context, boolean negate) throws QueryBuildException {
        BuildResult operand = filterBuilder.build(isNull.getOperand(), select, context, negate, false);
        negate = negate ^ isNull.getOperator() == ComparisonOperator.IS_NOT_NULL;

        if (operand.isSetPropertyRef()) {
            if (operand.getPropertyRef().getFilter().isPresent()) {
                throw new QueryBuildException("Predicates on the last step of the property reference of an " +
                        "isNull predicate are not supported.");
            }

            Node node;
            try {
                node = helper.getSchemaPathBuilder().build(operand.getPropertyRef(), context.getSchemaPath(), false);
            } catch (SchemaPathException e) {
                throw new QueryBuildException("Failed to build property reference.", e);
            }

            Node last = node.last();
            if (last.getSchemaObject() instanceof ValueObject valueObject
                    && valueObject.getValue().flatMap(Value::getColumn).isPresent()) {
                operand.update(helper.getContextBuilder().build(node, select, context));
                return BuildResult.of(Operators.isNull(operand.getExpression(), negate), operand);
            } else {
                SqlContext subQueryContext = SqlContext.of(context, helper);
                Select subQuery = Select.newInstance()
                        .select(subQueryContext.getTable().column("id"))
                        .from(subQueryContext.getTable());

                if (subQueryContext.getSchemaObject() instanceof FeatureType featureType
                        && helper.matches(Table.FEATURE, subQueryContext.getTable())) {
                    helper.getFeatureTypesBuilder().build(featureType, subQuery, subQueryContext);
                }

                SqlContext propertyContext = helper.getContextBuilder().build(
                        operand.getPropertyRef(), subQuery, subQueryContext, false);
                Joinable joinable = last.upStream()
                        .map(Node::getSchemaObject)
                        .filter(Joinable.class::isInstance)
                        .map(Joinable.class::cast)
                        .filter(candidate -> candidate.getJoin().isPresent() || candidate.getJoinTable().isPresent())
                        .findAny().orElse(null);
                if (joinable != null) {
                    org.citydb.sqlbuilder.schema.Table table = propertyContext.getTable();
                    if (joinable.getJoin().isPresent()) {
                        Join join = joinable.getJoin().get();
                        subQuery.where(helper.matches(join.getTable(), table) ?
                                table.column(join.getToColumn()).isNull() :
                                table.column(join.getFromColumn()).isNull());
                    } else if (joinable.getJoinTable().isPresent()) {
                        JoinTable joinTable = joinable.getJoinTable().get();
                        subQuery.where(helper.matches(joinTable.getTargetJoin().getTable(), table) ?
                                table.column(joinTable.getTargetJoin().getToColumn()).isNull() :
                                table.column(joinTable.getSourceJoin().getFromColumn()).isNull());
                    }
                } else {
                    subQuery.where(propertyContext.getTable().column("id").isNull());
                }

                propertyContext.getAndResetPredicates()
                        .forEach(subQuery::where);

                return BuildResult.of(In.of(context.getTable().column("id"), List.of(subQuery), negate));
            }
        } else {
            return BuildResult.of(Operators.isNull(operand.getExpression(), negate));
        }
    }

    private BuildResult build(org.citydb.query.filter.operation.Like like, Select select, SqlContext context, boolean negate) throws QueryBuildException {
        BuildResult operand = filterBuilder.build(like.getOperand(), select, context, negate);
        BuildResult pattern = filterBuilder.build(like.getPattern(), select, context, negate, false);

        operand.cast(Type.STRING, helper);
        operand.requireType(Type.STRING, () -> "A value of type '" + operand.getType() +
                "' cannot be used as operand of a like predicate.");
        pattern.requireType(Type.STRING, () -> "A value of type '" + operand.getType() +
                "' cannot be used as pattern of a like predicate.");

        if (operand.isSetExpression()
                && pattern.getExpression() instanceof ScalarExpression patternExpression) {
            negate = negate ^ like.getOperator() == ComparisonOperator.NOT_LIKE;
            return BuildResult.of(Like.of(operand.getExpression(), patternExpression,
                    containsEscapeCharacter(patternExpression) ?
                            StringLiteral.of(org.citydb.query.filter.operation.Like.ESCAPE_CHAR) :
                            null,
                    negate), operand);
        } else {
            throw new QueryBuildException("Failed to build like predicate.");
        }
    }

    private boolean containsEscapeCharacter(SqlObject object) {
        boolean[] containsEscapeCharacter = new boolean[1];
        object.accept(new SqlWalker() {
            @Override
            public void visit(Placeholder placeholder) {
                if (!containsEscapeCharacter[0]) {
                    containsEscapeCharacter[0] = placeholder.getValue()
                            .filter(String.class::isInstance)
                            .map(String.class::cast)
                            .map(value -> value.contains(org.citydb.query.filter.operation.Like.ESCAPE_CHAR))
                            .orElse(false);
                    super.visit(placeholder);
                }
            }
        });

        return containsEscapeCharacter[0];
    }
}
