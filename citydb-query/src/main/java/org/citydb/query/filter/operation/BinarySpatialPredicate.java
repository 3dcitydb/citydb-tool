/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter.operation;

import org.citydb.query.filter.common.FilterVisitor;
import org.citydb.query.filter.common.GeometryExpression;
import org.citydb.query.filter.common.Predicate;
import org.citydb.query.filter.common.SpatialPredicate;

import java.util.Objects;

public class BinarySpatialPredicate implements SpatialPredicate {
    private SpatialOperator operator;
    private final GeometryExpression leftOperand;
    private final GeometryExpression rightOperand;

    private BinarySpatialPredicate(SpatialOperator operator, GeometryExpression leftOperand, GeometryExpression rightOperand) {
        this.operator = Objects.requireNonNull(operator, "The spatial operator must not be null.");
        this.leftOperand = Objects.requireNonNull(leftOperand, "The left operand must not be null.");
        this.rightOperand = Objects.requireNonNull(rightOperand, "The right operand must not be null.");

        if (!SpatialOperator.BINARY_OPERATORS.contains(operator)) {
            throw new IllegalArgumentException("The spatial operator '" + operator + "' is not binary.");
        }
    }

    public static BinarySpatialPredicate of(GeometryExpression leftOperand, SpatialOperator operator, GeometryExpression rightOperand) {
        return new BinarySpatialPredicate(operator, leftOperand, rightOperand);
    }

    public SpatialOperator getOperator() {
        return operator;
    }

    public GeometryExpression getLeftOperand() {
        return leftOperand;
    }

    public GeometryExpression getRightOperand() {
        return rightOperand;
    }

    @Override
    public Predicate negate() {
        return switch (operator) {
            case INTERSECTS -> {
                operator = SpatialOperator.DISJOINT;
                yield this;
            }
            case DISJOINT -> {
                operator = SpatialOperator.INTERSECTS;
                yield this;
            }
            default -> Not.of(this);
        };
    }

    @Override
    public void accept(FilterVisitor visitor) {
        visitor.visit(this);
    }
}
