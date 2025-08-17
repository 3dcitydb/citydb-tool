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
