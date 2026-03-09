/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter.operation;

import org.citydb.query.filter.common.FilterVisitor;
import org.citydb.query.filter.common.ScalarExpression;
import org.citydb.query.filter.common.Sign;

import java.util.Objects;

public class ArithmeticExpression implements NumericExpression, ScalarExpression {
    private final ArithmeticOperator operator;
    private final NumericExpression leftOperand;
    private NumericExpression rightOperand;
    private Sign sign;

    private ArithmeticExpression(ArithmeticOperator operator, NumericExpression leftOperand, NumericExpression rightOperand) {
        this.operator = Objects.requireNonNull(operator, "The arithmetic operator must not be null.");
        this.leftOperand = Objects.requireNonNull(leftOperand, "The left operand must not be null.");
        this.rightOperand = Objects.requireNonNull(rightOperand, "The right operand must not be null.");
    }

    public static ArithmeticExpression of(NumericExpression leftOperand, ArithmeticOperator operator, NumericExpression rightOperand) {
        return new ArithmeticExpression(operator, leftOperand, rightOperand);
    }

    public ArithmeticOperator getOperator() {
        return operator;
    }

    public NumericExpression getLeftOperand() {
        return leftOperand;
    }

    public NumericExpression getRightOperand() {
        return rightOperand;
    }

    ArithmeticExpression fluentAppend(ArithmeticOperator operator, NumericExpression operand) {
        if (operator != null) {
            if (operator.takesPrecedenceOver(this.operator)) {
                rightOperand = new ArithmeticExpression(operator, rightOperand, operand);
                return this;
            } else {
                return new ArithmeticExpression(operator, this, operand);
            }
        } else {
            return this;
        }
    }

    @Override
    public Sign getSign() {
        return sign != null ? sign : Sign.PLUS;
    }

    @Override
    public NumericExpression negate() {
        sign = sign == null ? Sign.MINUS : null;
        return this;
    }

    @Override
    public void accept(FilterVisitor visitor) {
        visitor.visit(this);
    }
}
