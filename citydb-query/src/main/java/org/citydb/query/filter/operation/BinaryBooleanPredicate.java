/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter.operation;

import org.citydb.query.filter.common.FilterVisitor;
import org.citydb.query.filter.common.LogicalPredicate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class BinaryBooleanPredicate implements LogicalPredicate {
    private final BooleanOperator operator;
    private final List<BooleanExpression> operands;

    private BinaryBooleanPredicate(BooleanOperator operator, List<? extends BooleanExpression> operands) {
        this.operator = Objects.requireNonNull(operator, "The boolean operator must not be null.");
        this.operands = new ArrayList<>(Objects.requireNonNull(operands, "The operands list must not be null."));

        if (!BooleanOperator.BINARY_OPERATORS.contains(operator)) {
            throw new IllegalArgumentException("The boolean operator '" + operator + "' is not binary.");
        } else if (operands.isEmpty()) {
            throw new IllegalArgumentException("The operands list must not be empty.");
        }
    }

    public static BinaryBooleanPredicate of(BooleanOperator operator, List<? extends BooleanExpression> operands) {
        return new BinaryBooleanPredicate(operator, operands);
    }

    public static BinaryBooleanPredicate of(BooleanOperator operator, BooleanExpression... operands) {
        return new BinaryBooleanPredicate(operator, operands != null ? Arrays.asList(operands) : null);
    }

    public static BinaryBooleanPredicate of(BooleanExpression leftOperand, BooleanOperator operator, BooleanExpression rightOperand) {
        List<BooleanExpression> operands = new ArrayList<>();
        operands.add(Objects.requireNonNull(leftOperand, "The left operand must not be null."));
        operands.add(Objects.requireNonNull(rightOperand, "The right operand must not be null."));
        return new BinaryBooleanPredicate(operator, operands);
    }

    public BooleanOperator getOperator() {
        return operator;
    }

    public List<BooleanExpression> getOperands() {
        return operands;
    }

    public BinaryBooleanPredicate add(List<? extends BooleanExpression> operands) {
        if (operands != null && !operands.isEmpty()) {
            operands.stream()
                    .filter(Objects::nonNull)
                    .forEach(this.operands::add);
        }

        return this;
    }

    public BinaryBooleanPredicate add(BooleanExpression... operands) {
        return operands != null ? add(Arrays.asList(operands)) : this;
    }

    BinaryBooleanPredicate fluentAnd(BooleanExpression operand) {
        if (operand != null) {
            if (operator == BooleanOperator.OR) {
                int index = operands.size() - 1;
                operands.set(index, Operators.and(operands.get(index), operand));
            } else {
                operands.add(operand);
            }
        }

        return this;
    }

    BinaryBooleanPredicate fluentOr(BooleanExpression operand) {
        if (operand != null) {
            if (operator == BooleanOperator.AND) {
                return Operators.or(this, operand);
            } else {
                operands.add(operand);
            }
        }

        return this;
    }

    @Override
    public Not negate() {
        return Not.of(this);
    }

    @Override
    public void accept(FilterVisitor visitor) {
        visitor.visit(this);
    }
}
