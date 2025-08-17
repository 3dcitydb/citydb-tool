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

import org.citydb.query.filter.common.ComparisonPredicate;
import org.citydb.query.filter.common.FilterVisitor;
import org.citydb.query.filter.common.ScalarExpression;

import java.util.Objects;

public class BinaryComparisonPredicate implements ComparisonPredicate {
    private ComparisonOperator operator;
    private final ScalarExpression leftOperand;
    private final ScalarExpression rightOperand;

    private BinaryComparisonPredicate(ComparisonOperator operator, ScalarExpression leftOperand, ScalarExpression rightOperand, boolean negate) {
        this.operator = Objects.requireNonNull(operator, "The comparison operator must not be null.");
        this.leftOperand = Objects.requireNonNull(leftOperand, "The left operand must not be null.");
        this.rightOperand = Objects.requireNonNull(rightOperand, "The right operand must not be null.");

        if (!ComparisonOperator.BINARY_OPERATORS.contains(operator)) {
            throw new IllegalArgumentException("The comparison operator '" + operator + "' is not binary.");
        } else if (negate) {
            this.operator = operator.negate();
        }
    }

    public static BinaryComparisonPredicate of(ScalarExpression leftOperand, ComparisonOperator operator, ScalarExpression rightOperand, boolean negate) {
        return new BinaryComparisonPredicate(operator, leftOperand, rightOperand, negate);
    }

    public static BinaryComparisonPredicate of(ScalarExpression leftOperand, ComparisonOperator operator, ScalarExpression rightOperand) {
        return new BinaryComparisonPredicate(operator, leftOperand, rightOperand, false);
    }

    public ComparisonOperator getOperator() {
        return operator;
    }

    public ScalarExpression getLeftOperand() {
        return leftOperand;
    }

    public ScalarExpression getRightOperand() {
        return rightOperand;
    }

    @Override
    public BinaryComparisonPredicate negate() {
        operator = operator.negate();
        return this;
    }

    @Override
    public void accept(FilterVisitor visitor) {
        visitor.visit(this);
    }
}
