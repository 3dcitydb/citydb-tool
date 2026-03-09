/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter.operation;

import org.citydb.query.filter.common.FilterVisitor;
import org.citydb.query.filter.common.LogicalPredicate;

import java.util.Objects;

public class Not implements LogicalPredicate {
    private final BooleanExpression operand;

    private Not(BooleanExpression operand) {
        this.operand = Objects.requireNonNull(operand, "The operand must not be null.");
    }

    public static Not of(BooleanExpression operand) {
        return new Not(operand);
    }

    public BooleanExpression getOperand() {
        return operand;
    }

    @Override
    public BooleanExpression negate() {
        return operand;
    }

    @Override
    public void accept(FilterVisitor visitor) {
        visitor.visit(this);
    }
}
