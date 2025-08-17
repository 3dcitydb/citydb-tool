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

package org.citydb.query.filter.common;

import org.citydb.query.filter.literal.Literal;
import org.citydb.query.filter.operation.BinaryComparisonPredicate;
import org.citydb.query.filter.operation.In;
import org.citydb.query.filter.operation.IsNull;
import org.citydb.query.filter.operation.Operators;

import java.util.Arrays;
import java.util.List;

public interface ScalarExpression extends Argument {

    default BinaryComparisonPredicate eq(Object operand) {
        return Operators.eq(this, operand instanceof ScalarExpression expression ?
                expression :
                Literal.ofScalar(operand));
    }

    default BinaryComparisonPredicate ne(Object operand) {
        return Operators.ne(this, operand instanceof ScalarExpression expression ?
                expression :
                Literal.ofScalar(operand));
    }

    default BinaryComparisonPredicate lt(Object operand) {
        return Operators.lt(this, operand instanceof ScalarExpression expression ?
                expression :
                Literal.ofScalar(operand));
    }

    default BinaryComparisonPredicate nl(Object operand) {
        return Operators.nl(this, operand instanceof ScalarExpression expression ?
                expression :
                Literal.ofScalar(operand));
    }

    default BinaryComparisonPredicate le(Object operand) {
        return Operators.le(this, operand instanceof ScalarExpression expression ?
                expression :
                Literal.ofScalar(operand));
    }

    default BinaryComparisonPredicate nle(Object operand) {
        return Operators.nle(this, operand instanceof ScalarExpression expression ?
                expression :
                Literal.ofScalar(operand));
    }

    default BinaryComparisonPredicate gt(Object operand) {
        return Operators.gt(this, operand instanceof ScalarExpression expression ?
                expression :
                Literal.ofScalar(operand));
    }

    default BinaryComparisonPredicate ng(Object operand) {
        return Operators.ng(this, operand instanceof ScalarExpression expression ?
                expression :
                Literal.ofScalar(operand));
    }

    default BinaryComparisonPredicate ge(Object operand) {
        return Operators.ge(this, operand instanceof ScalarExpression expression ?
                expression :
                Literal.ofScalar(operand));
    }

    default BinaryComparisonPredicate nge(Object operand) {
        return Operators.nge(this, operand instanceof ScalarExpression expression ?
                expression :
                Literal.ofScalar(operand));
    }

    default In in(List<ScalarExpression> values) {
        return Operators.in(this, values);
    }

    default In in(Object... values) {
        return in(values != null ?
                Arrays.stream(values)
                        .map(Literal::ofScalar)
                        .toList() :
                null);
    }

    default In notIn(List<ScalarExpression> values) {
        return Operators.notIn(this, values);
    }

    default In notIn(Object... values) {
        return notIn(values != null ?
                Arrays.stream(values)
                        .map(Literal::ofScalar)
                        .toList() :
                null);
    }

    default IsNull isNull() {
        return Operators.isNull(this);
    }

    default IsNull isNotNull() {
        return Operators.isNotNull(this);
    }
}
