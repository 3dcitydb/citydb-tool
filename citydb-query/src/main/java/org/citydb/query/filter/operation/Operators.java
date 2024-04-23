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

package org.citydb.query.filter.operation;

import org.citydb.query.filter.common.Expression;
import org.citydb.query.filter.common.GeometryExpression;
import org.citydb.query.filter.common.PatternExpression;
import org.citydb.query.filter.common.ScalarExpression;
import org.citydb.query.filter.literal.StringLiteral;

import java.util.Arrays;
import java.util.List;

public class Operators {

    public static ArithmeticExpression plus(NumericExpression leftOperand, NumericExpression rightOperand) {
        return ArithmeticExpression.of(leftOperand, ArithmeticOperator.PLUS, rightOperand);
    }

    public static ArithmeticExpression minus(NumericExpression leftOperand, NumericExpression rightOperand) {
        return ArithmeticExpression.of(leftOperand, ArithmeticOperator.MINUS, rightOperand);
    }

    public static ArithmeticExpression multiplyBy(NumericExpression leftOperand, NumericExpression rightOperand) {
        return ArithmeticExpression.of(leftOperand, ArithmeticOperator.MULTIPLY, rightOperand);
    }

    public static ArithmeticExpression divideBy(NumericExpression leftOperand, NumericExpression rightOperand) {
        return ArithmeticExpression.of(leftOperand, ArithmeticOperator.DIVIDE, rightOperand);
    }

    public static ArithmeticExpression power(NumericExpression leftOperand, NumericExpression rightOperand) {
        return ArithmeticExpression.of(leftOperand, ArithmeticOperator.POWER, rightOperand);
    }

    public static ArithmeticExpression modulo(NumericExpression leftOperand, NumericExpression rightOperand) {
        return ArithmeticExpression.of(leftOperand, ArithmeticOperator.MODULO, rightOperand);
    }

    public static ArithmeticExpression intDivideBy(NumericExpression leftOperand, NumericExpression rightOperand) {
        return ArithmeticExpression.of(leftOperand, ArithmeticOperator.INT_DIVIDE, rightOperand);
    }

    public static BinaryComparisonPredicate eq(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return BinaryComparisonPredicate.of(leftOperand, ComparisonOperator.EQUAL_TO, rightOperand);
    }

    public static BinaryComparisonPredicate ne(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return BinaryComparisonPredicate.of(leftOperand, ComparisonOperator.NOT_EQUAL_TO, rightOperand);
    }

    public static BinaryComparisonPredicate lt(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return BinaryComparisonPredicate.of(leftOperand, ComparisonOperator.LESS_THAN, rightOperand);
    }

    public static BinaryComparisonPredicate nl(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return BinaryComparisonPredicate.of(leftOperand, ComparisonOperator.LESS_THAN.negate(), rightOperand);
    }

    public static BinaryComparisonPredicate le(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return BinaryComparisonPredicate.of(leftOperand, ComparisonOperator.LESS_THAN_OR_EQUAL_TO, rightOperand);
    }

    public static BinaryComparisonPredicate nle(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return BinaryComparisonPredicate.of(leftOperand, ComparisonOperator.LESS_THAN_OR_EQUAL_TO.negate(), rightOperand);
    }

    public static BinaryComparisonPredicate gt(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return BinaryComparisonPredicate.of(leftOperand, ComparisonOperator.GREATER_THAN, rightOperand);
    }

    public static BinaryComparisonPredicate ng(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return BinaryComparisonPredicate.of(leftOperand, ComparisonOperator.GREATER_THAN.negate(), rightOperand);
    }

    public static BinaryComparisonPredicate ge(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return BinaryComparisonPredicate.of(leftOperand, ComparisonOperator.GREATER_THAN_OR_EQUAL_TO, rightOperand);
    }

    public static BinaryComparisonPredicate nge(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return BinaryComparisonPredicate.of(leftOperand, ComparisonOperator.GREATER_THAN_OR_EQUAL_TO.negate(), rightOperand);
    }

    public static Between between(NumericExpression operand, NumericExpression lowerBound, NumericExpression upperBound) {
        return Between.of(operand, lowerBound, upperBound);
    }

    public static Between notBetween(NumericExpression operand, NumericExpression lowerBound, NumericExpression upperBound) {
        return Between.of(operand, lowerBound, upperBound, true);
    }

    public static In in(ScalarExpression operand, List<? extends ScalarExpression> values) {
        return In.of(operand, values);
    }

    public static In in(ScalarExpression operand, ScalarExpression... values) {
        return In.of(operand, values);
    }

    public static In notIn(ScalarExpression operand, List<? extends ScalarExpression> values) {
        return In.of(operand, values, true);
    }

    public static In notIn(ScalarExpression operand, ScalarExpression... values) {
        return notIn(operand, values != null ? Arrays.asList(values) : null);
    }

    public static IsNull isNull(Expression operand) {
        return IsNull.of(operand);
    }

    public static IsNull isNotNull(Expression operand) {
        return IsNull.of(operand, true);
    }

    public static Like like(StringLiteral operand, PatternExpression pattern) {
        return Like.of(operand, pattern);
    }

    public static Like notLike(StringLiteral operand, PatternExpression pattern) {
        return Like.of(operand, pattern, true);
    }

    public static SpatialPredicate intersects(GeometryExpression leftOperand, GeometryExpression rightOperand) {
        return SpatialPredicate.of(leftOperand, SpatialOperator.INTERSECTS, rightOperand);
    }

    public static SpatialPredicate contains(GeometryExpression leftOperand, GeometryExpression rightOperand) {
        return SpatialPredicate.of(leftOperand, SpatialOperator.CONTAINS, rightOperand);
    }

    public static SpatialPredicate crosses(GeometryExpression leftOperand, GeometryExpression rightOperand) {
        return SpatialPredicate.of(leftOperand, SpatialOperator.CROSSES, rightOperand);
    }

    public static SpatialPredicate disjoint(GeometryExpression leftOperand, GeometryExpression rightOperand) {
        return SpatialPredicate.of(leftOperand, SpatialOperator.DISJOINT, rightOperand);
    }

    public static SpatialPredicate equals(GeometryExpression leftOperand, GeometryExpression rightOperand) {
        return SpatialPredicate.of(leftOperand, SpatialOperator.EQUALS, rightOperand);
    }

    public static SpatialPredicate overlaps(GeometryExpression leftOperand, GeometryExpression rightOperand) {
        return SpatialPredicate.of(leftOperand, SpatialOperator.OVERLAPS, rightOperand);
    }

    public static SpatialPredicate touches(GeometryExpression leftOperand, GeometryExpression rightOperand) {
        return SpatialPredicate.of(leftOperand, SpatialOperator.TOUCHES, rightOperand);
    }

    public static SpatialPredicate within(GeometryExpression leftOperand, GeometryExpression rightOperand) {
        return SpatialPredicate.of(leftOperand, SpatialOperator.WITHIN, rightOperand);
    }

    public static SpatialPredicate dWithin(GeometryExpression leftOperand, GeometryExpression rightOperand) {
        return SpatialPredicate.of(leftOperand, SpatialOperator.DWITHIN, rightOperand);
    }

    public static SpatialPredicate beyond(GeometryExpression leftOperand, GeometryExpression rightOperand) {
        return SpatialPredicate.of(leftOperand, SpatialOperator.BEYOND, rightOperand);
    }

    public static BinaryBooleanPredicate and(List<? extends BooleanExpression> operands) {
        return BinaryBooleanPredicate.of(BooleanOperator.AND, operands);
    }

    public static BinaryBooleanPredicate and(BooleanExpression... operands) {
        return BinaryBooleanPredicate.of(BooleanOperator.AND, operands);
    }

    public static BinaryBooleanPredicate or(List<? extends BooleanExpression> operands) {
        return BinaryBooleanPredicate.of(BooleanOperator.OR, operands);
    }

    public static BinaryBooleanPredicate or(BooleanExpression... operands) {
        return BinaryBooleanPredicate.of(BooleanOperator.OR, operands);
    }

    public static Not not(BooleanExpression operand) {
        return Not.of(operand);
    }
}
