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
import org.citydb.query.filter.common.Predicate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class BinaryBooleanPredicate implements Predicate {
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
