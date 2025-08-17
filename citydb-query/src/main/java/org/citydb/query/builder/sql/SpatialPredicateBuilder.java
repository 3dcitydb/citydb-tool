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

import org.citydb.database.srs.SpatialReference;
import org.citydb.database.srs.SrsException;
import org.citydb.model.geometry.SpatialObject;
import org.citydb.query.builder.QueryBuildException;
import org.citydb.query.builder.common.Type;
import org.citydb.query.filter.common.Distance;
import org.citydb.query.filter.common.SpatialPredicate;
import org.citydb.query.filter.operation.BinarySpatialPredicate;
import org.citydb.query.filter.operation.DWithin;
import org.citydb.query.filter.operation.SpatialOperator;
import org.citydb.sqlbuilder.literal.Placeholder;
import org.citydb.sqlbuilder.literal.ScalarExpression;
import org.citydb.sqlbuilder.operation.BooleanExpression;
import org.citydb.sqlbuilder.operation.Not;
import org.citydb.sqlbuilder.query.Select;

public class SpatialPredicateBuilder {
    private final FilterBuilder filterBuilder;
    private final BuilderHelper helper;
    private final SpatialReference databaseSrs;
    private SpatialReference filterSrs;

    private SpatialPredicateBuilder(FilterBuilder filterBuilder, BuilderHelper helper) {
        this.filterBuilder = filterBuilder;
        this.helper = helper;
        databaseSrs = filterSrs = helper.getDatabaseAdapter().getDatabaseMetadata().getSpatialReference();
    }

    static SpatialPredicateBuilder of(FilterBuilder filterBuilder, BuilderHelper helper) {
        return new SpatialPredicateBuilder(filterBuilder, helper);
    }

    void setFilterSrs(SpatialReference filterSrs) {
        this.filterSrs = filterSrs;
    }

    BuildResult build(SpatialPredicate predicate, Select select, SqlContext context, boolean negate) throws QueryBuildException {
        if (predicate instanceof BinarySpatialPredicate binarySpatialPredicate) {
            return build(binarySpatialPredicate, select, context, negate);
        } else if (predicate instanceof DWithin dWithin) {
            return build(dWithin, select, context, negate);
        } else {
            throw new QueryBuildException("Unsupported spatial predicate.");
        }
    }

    private BuildResult build(BinarySpatialPredicate predicate, Select select, SqlContext context, boolean negate) throws QueryBuildException {
        BuildResult leftOperand = filterBuilder.build(predicate.getLeftOperand(), select, context, negate);
        BuildResult rightOperand = filterBuilder.build(predicate.getRightOperand(), select, context, negate);

        helper.cast(leftOperand, rightOperand);
        leftOperand.requireType(Type.SPATIAL_TYPES, () -> "A value of type '" + leftOperand.getType() +
                "' cannot be used as left operand of a binary spatial predicate.");
        rightOperand.requireType(Type.SPATIAL_TYPES, () -> "A value of type '" + rightOperand.getType() +
                "' cannot be used as right operand of a binary spatial predicate.");

        if (leftOperand.getExpression() instanceof ScalarExpression leftExpression
                && rightOperand.getExpression() instanceof ScalarExpression rightExpression) {
            SpatialOperator operator = negate(predicate.getOperator(), negate);
            negate = negate ^ operator != predicate.getOperator();

            SpatialObject leftLiteral = helper.getSpatialLiteral(predicate.getLeftOperand());
            if (leftLiteral != null && helper.getOrSetSRID(leftLiteral, filterSrs) != databaseSrs.getSRID()) {
                leftExpression = helper.getSpatialOperationHelper().transform(leftExpression, databaseSrs.getSRID());
            }

            SpatialObject rightLiteral = helper.getSpatialLiteral(predicate.getRightOperand());
            if (rightLiteral != null && helper.getOrSetSRID(rightLiteral, filterSrs) != databaseSrs.getSRID()) {
                rightExpression = helper.getSpatialOperationHelper().transform(rightExpression, databaseSrs.getSRID());
            }

            BooleanExpression expression;
            if (leftOperand.getType() == Type.ENVELOPE
                    && rightOperand.getType() == Type.ENVELOPE
                    && (operator == SpatialOperator.INTERSECTS
                    || operator == SpatialOperator.DISJOINT)) {
                negate = negate ^ operator == SpatialOperator.DISJOINT;
                expression = helper.getSpatialOperationHelper().bbox(leftExpression, rightExpression);
            } else {
                expression = switch (operator) {
                    case CONTAINS -> helper.getSpatialOperationHelper().contains(leftExpression, rightExpression);
                    case CROSSES -> helper.getSpatialOperationHelper().crosses(leftExpression, rightExpression);
                    case DISJOINT -> helper.getSpatialOperationHelper().disjoint(leftExpression, rightExpression);
                    case EQUALS -> helper.getSpatialOperationHelper().equals(leftExpression, rightExpression);
                    case INTERSECTS -> helper.getSpatialOperationHelper().intersects(leftExpression, rightExpression);
                    case OVERLAPS -> helper.getSpatialOperationHelper().overlaps(leftExpression, rightExpression);
                    case TOUCHES -> helper.getSpatialOperationHelper().touches(leftExpression, rightExpression);
                    case WITHIN -> helper.getSpatialOperationHelper().within(leftExpression, rightExpression);
                    default -> throw new QueryBuildException("Unsupported operator '" +
                            predicate.getOperator().getTextToken() + "' used in binary spatial predicate.");
                };
            }

            return BuildResult.of(negate ? Not.of(expression) : expression, leftOperand, rightOperand);
        } else {
            throw new QueryBuildException("Failed to build binary spatial predicate.");
        }
    }

    private BuildResult build(DWithin dWithin, Select select, SqlContext context, boolean negate) throws QueryBuildException {
        BuildResult leftOperand = filterBuilder.build(dWithin.getLeftOperand(), select, context, negate);
        BuildResult rightOperand = filterBuilder.build(dWithin.getRightOperand(), select, context, negate);

        helper.cast(leftOperand, rightOperand);
        leftOperand.requireType(Type.SPATIAL_TYPES, () -> "A value of type '" + leftOperand.getType() +
                "' cannot be used as left operand of a spatial distance predicate.");
        rightOperand.requireType(Type.SPATIAL_TYPES, () -> "A value of type '" + rightOperand.getType() +
                "' cannot be used as right operand of a spatial distance predicate.");

        if (leftOperand.getExpression() instanceof ScalarExpression leftExpression
                && rightOperand.getExpression() instanceof ScalarExpression rightExpression) {
            negate = negate ^ dWithin.getOperator() == SpatialOperator.BEYOND;

            SpatialObject leftLiteral = helper.getSpatialLiteral(dWithin.getLeftOperand());
            if (leftLiteral != null && helper.getOrSetSRID(leftLiteral, filterSrs) != databaseSrs.getSRID()) {
                leftExpression = helper.getSpatialOperationHelper().transform(leftExpression, databaseSrs.getSRID());
            }

            SpatialObject rightLiteral = helper.getSpatialLiteral(dWithin.getRightOperand());
            if (rightLiteral != null && helper.getOrSetSRID(rightLiteral, filterSrs) != databaseSrs.getSRID()) {
                rightExpression = helper.getSpatialOperationHelper().transform(rightExpression, databaseSrs.getSRID());
            }

            Placeholder distance = Placeholder.of(getDistance(dWithin.getDistance()));
            return BuildResult.of(negate ?
                            helper.getSpatialOperationHelper().beyond(leftExpression, rightExpression, distance) :
                            helper.getSpatialOperationHelper().dWithin(leftExpression, rightExpression, distance),
                    leftOperand, rightOperand);
        } else {
            throw new QueryBuildException("Failed to build spatial distance predicate.");
        }
    }

    private double getDistance(Distance distance) throws QueryBuildException {
        if (distance.getUnit().isPresent()) {
            try {
                return helper.getDatabaseAdapter().getGeometryAdapter().getSrsHelper()
                        .convert(distance.getValue(), distance.getUnit().get());
            } catch (SrsException e) {
                throw new QueryBuildException("Failed to convert distance value.", e);
            }
        } else {
            return distance.getValue();
        }
    }

    private SpatialOperator negate(SpatialOperator operator, boolean negate) {
        if (negate) {
            return switch (operator) {
                case INTERSECTS -> SpatialOperator.DISJOINT;
                case DISJOINT -> SpatialOperator.INTERSECTS;
                default -> operator;
            };
        } else {
            return operator;
        }
    }
}
