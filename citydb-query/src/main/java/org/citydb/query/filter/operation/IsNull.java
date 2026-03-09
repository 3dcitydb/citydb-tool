/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter.operation;

import org.citydb.query.filter.common.ComparisonPredicate;
import org.citydb.query.filter.common.Expression;
import org.citydb.query.filter.common.FilterVisitor;

import java.util.Objects;

public class IsNull implements ComparisonPredicate {
    private final Expression operand;
    private ComparisonOperator operator;

    private IsNull(Expression operand, boolean negate) {
        this.operand = Objects.requireNonNull(operand, "The operand must not be null.");
        this.operator = negate ? ComparisonOperator.IS_NULL.negate() : ComparisonOperator.IS_NULL;
    }

    public static IsNull of(Expression operand, boolean negate) {
        return new IsNull(operand, negate);
    }

    public static IsNull of(Expression operand) {
        return new IsNull(operand, false);
    }

    public Expression getOperand() {
        return operand;
    }

    public ComparisonOperator getOperator() {
        return operator;
    }

    @Override
    public IsNull negate() {
        operator = operator.negate();
        return this;
    }

    @Override
    public void accept(FilterVisitor visitor) {
        visitor.visit(this);
    }
}
