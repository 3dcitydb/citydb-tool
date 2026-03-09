/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter.operation;

import org.citydb.query.filter.common.ComparisonPredicate;
import org.citydb.query.filter.common.FilterVisitor;
import org.citydb.query.filter.common.ScalarExpression;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class In implements ComparisonPredicate {
    private final ScalarExpression operand;
    private final List<ScalarExpression> values;
    private ComparisonOperator operator;

    private In(ScalarExpression operand, List<? extends ScalarExpression> values, boolean negate) {
        this.operand = operand;
        this.values = new ArrayList<>(Objects.requireNonNull(values, "The values list must not be null."));
        this.operator = negate ? ComparisonOperator.IN.negate() : ComparisonOperator.IN;

        if (values.isEmpty()) {
            throw new IllegalArgumentException("The values list must not be empty.");
        }
    }

    public static In of(ScalarExpression operand, List<? extends ScalarExpression> values, boolean negate) {
        return new In(operand, values, negate);
    }

    public static In of(ScalarExpression operand, List<? extends ScalarExpression> values) {
        return new In(operand, values, false);
    }

    public static In of(ScalarExpression operand, ScalarExpression... values) {
        return new In(operand, values != null ? Arrays.asList(values) : null, false);
    }

    public ScalarExpression getOperand() {
        return operand;
    }

    public List<ScalarExpression> getValues() {
        return values;
    }

    public ComparisonOperator getOperator() {
        return operator;
    }

    public In add(List<? extends ScalarExpression> values) {
        if (values != null && !values.isEmpty()) {
            values.stream()
                    .filter(Objects::nonNull)
                    .forEach(this.values::add);
        }

        return this;
    }

    public In add(ScalarExpression... values) {
        return values != null ? add(Arrays.asList(values)) : this;
    }

    @Override
    public In negate() {
        operator = operator.negate();
        return this;
    }

    @Override
    public void accept(FilterVisitor visitor) {
        visitor.visit(this);
    }
}
