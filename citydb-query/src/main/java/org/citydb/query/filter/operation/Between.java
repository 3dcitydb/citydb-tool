/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter.operation;

import org.citydb.query.filter.common.ComparisonPredicate;
import org.citydb.query.filter.common.FilterVisitor;

import java.util.Objects;

public class Between implements ComparisonPredicate {
    private final NumericExpression operand;
    private final NumericExpression lowerBound;
    private final NumericExpression upperBound;
    private ComparisonOperator operator;

    private Between(NumericExpression operand, NumericExpression lowerBound, NumericExpression upperBound, boolean negate) {
        this.operand = Objects.requireNonNull(operand, "The operand must not be null.");
        this.lowerBound = Objects.requireNonNull(lowerBound, "The lower bound must not be null.");
        this.upperBound = Objects.requireNonNull(upperBound, "The upper bound must not be null.");
        this.operator = negate ? ComparisonOperator.BETWEEN.negate() : ComparisonOperator.BETWEEN;
    }

    public static Between of(NumericExpression operand, NumericExpression lowerBound, NumericExpression upperBound, boolean negate) {
        return new Between(operand, lowerBound, upperBound, negate);
    }

    public static Between of(NumericExpression operand, NumericExpression lowerBound, NumericExpression upperBound) {
        return new Between(operand, lowerBound, upperBound, false);
    }

    public NumericExpression getOperand() {
        return operand;
    }

    public NumericExpression getLowerBound() {
        return lowerBound;
    }

    public NumericExpression getUpperBound() {
        return upperBound;
    }

    public ComparisonOperator getOperator() {
        return operator;
    }

    @Override
    public Between negate() {
        operator = operator.negate();
        return this;
    }

    @Override
    public void accept(FilterVisitor visitor) {
        visitor.visit(this);
    }
}
