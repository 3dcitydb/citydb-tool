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

import java.util.Objects;

public class Not implements Predicate {
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
