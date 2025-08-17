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

import java.util.Objects;

public class Between implements ComparisonPredicate {
    private final NumericExpression operand;
    private final NumericExpression lowerBound;
    private final NumericExpression upperBound;
    private ComparisonOperator operator;

    private Between(NumericExpression operand, NumericExpression lowerBound, NumericExpression upperBound, boolean negate) {
        this.operand = Objects.requireNonNull(operand, "The operand must not be null.");
        this.lowerBound = Objects.requireNonNull(lowerBound, "The lower bound must not be null.");
        this.upperBound = Objects.requireNonNull(upperBound, "The upper bound must not be null.");
        this.operator = negate ? ComparisonOperator.BETWEEN.negate() : ComparisonOperator.BETWEEN;
    }

    public static Between of(NumericExpression operand, NumericExpression lowerBound, NumericExpression upperBound, boolean negate) {
        return new Between(operand, lowerBound, upperBound, negate);
    }

    public static Between of(NumericExpression operand, NumericExpression lowerBound, NumericExpression upperBound) {
        return new Between(operand, lowerBound, upperBound, false);
    }

    public NumericExpression getOperand() {
        return operand;
    }

    public NumericExpression getLowerBound() {
        return lowerBound;
    }

    public NumericExpression getUpperBound() {
        return upperBound;
    }

    public ComparisonOperator getOperator() {
        return operator;
    }

    @Override
    public Between negate() {
        operator = operator.negate();
        return this;
    }

    @Override
    public void accept(FilterVisitor visitor) {
        visitor.visit(this);
    }
}
