/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter.operation;

import org.citydb.query.filter.common.Distance;
import org.citydb.query.filter.common.FilterVisitor;
import org.citydb.query.filter.common.GeometryExpression;
import org.citydb.query.filter.common.SpatialPredicate;

import java.util.Objects;

public class DWithin implements SpatialPredicate {
    private final GeometryExpression leftOperand;
    private final GeometryExpression rightOperand;
    private final Distance distance;
    private SpatialOperator operator;

    private DWithin(GeometryExpression leftOperand, GeometryExpression rightOperand, Distance distance, boolean negate) {
        this.leftOperand = Objects.requireNonNull(leftOperand, "The left operand must not be null.");
        this.rightOperand = Objects.requireNonNull(rightOperand, "The right operand must not be null.");
        this.distance = Objects.requireNonNull(distance, "The distance must not be null.");
        operator = negate ? SpatialOperator.BEYOND : SpatialOperator.DWITHIN;
    }

    public static DWithin of(GeometryExpression leftOperand, GeometryExpression rightOperand, Distance distance, boolean negate) {
        return new DWithin(leftOperand, rightOperand, distance, negate);
    }

    public static DWithin of(GeometryExpression leftOperand, GeometryExpression rightOperand, Distance distance) {
        return new DWithin(leftOperand, rightOperand, distance, false);
    }

    public GeometryExpression getLeftOperand() {
        return leftOperand;
    }

    public GeometryExpression getRightOperand() {
        return rightOperand;
    }

    public Distance getDistance() {
        return distance;
    }

    public SpatialOperator getOperator() {
        return operator;
    }

    @Override
    public DWithin negate() {
        operator = operator == SpatialOperator.DWITHIN ?
                SpatialOperator.BEYOND :
                SpatialOperator.DWITHIN;
        return this;
    }

    @Override
    public void accept(FilterVisitor visitor) {
        visitor.visit(this);
    }
}
