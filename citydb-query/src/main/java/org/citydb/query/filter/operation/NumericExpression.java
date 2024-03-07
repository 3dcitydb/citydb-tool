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

import org.citydb.query.filter.common.Expression;
import org.citydb.query.filter.common.Sign;
import org.citydb.query.filter.literal.NumericLiteral;

public interface NumericExpression extends Expression {

    Sign getSign();

    NumericExpression negate();

    private static NumericLiteral literal(Object value) {
        return NumericLiteral.of(value)
                .orElseThrow(() -> new IllegalArgumentException("Invalid numeric operand '" + value + "'."));
    }

    default ArithmeticExpression plus(Object operand) {
        return append(ArithmeticOperator.PLUS, operand);
    }

    default ArithmeticExpression minus(Object operand) {
        return append(ArithmeticOperator.MINUS, operand);
    }

    default ArithmeticExpression multiplyBy(Object operand) {
        return append(ArithmeticOperator.MULTIPLY, operand);
    }

    default ArithmeticExpression divideBy(Object operand) {
        return append(ArithmeticOperator.DIVIDE, operand);
    }

    default ArithmeticExpression power(Object operand) {
        return append(ArithmeticOperator.POWER, operand);
    }

    default ArithmeticExpression modulo(Object operand) {
        return append(ArithmeticOperator.MODULO, operand);
    }

    default ArithmeticExpression intDivideBy(Object operand) {
        return append(ArithmeticOperator.INT_DIVIDE, operand);
    }

    default ArithmeticExpression append(ArithmeticOperator operator, Object operand) {
        NumericExpression rightOperand = operand instanceof NumericExpression expression ?
                expression :
                literal(operand);

        return this instanceof ArithmeticExpression operation ?
                operation.fluentAppend(operator, rightOperand) :
                ArithmeticExpression.of(this, operator, rightOperand);
    }

    default Between between(Object lowerBound, Object upperBound) {
        return Operators.between(this,
                lowerBound instanceof NumericExpression expression ? expression : literal(lowerBound),
                upperBound instanceof NumericExpression expression ? expression : literal(upperBound));
    }

    default Between notBetween(Object lowerBound, Object upperBound) {
        return Operators.notBetween(this,
                lowerBound instanceof NumericExpression expression ? expression : literal(lowerBound),
                upperBound instanceof NumericExpression expression ? expression : literal(upperBound));
    }
}
