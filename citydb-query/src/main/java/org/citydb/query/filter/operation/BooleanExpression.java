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

import org.citydb.query.filter.common.Argument;

public interface BooleanExpression extends Argument {

    default BinaryBooleanPredicate and(BooleanExpression operand) {
        return this instanceof BinaryBooleanPredicate predicate ?
                predicate.fluentAnd(operand) :
                Operators.and(this, operand);
    }

    default BinaryBooleanPredicate andNot(BooleanExpression operand) {
        return and(Operators.not(operand));
    }

    default BinaryBooleanPredicate or(BooleanExpression operand) {
        return this instanceof BinaryBooleanPredicate predicate ?
                predicate.fluentOr(operand) :
                Operators.or(this, operand);
    }

    default BinaryBooleanPredicate orNot(BooleanExpression operand) {
        return or(Operators.not(operand));
    }

    default Not not() {
        return Operators.not(this);
    }
}
