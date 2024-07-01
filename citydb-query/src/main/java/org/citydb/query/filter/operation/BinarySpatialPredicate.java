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
