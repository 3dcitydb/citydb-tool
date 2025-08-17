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
