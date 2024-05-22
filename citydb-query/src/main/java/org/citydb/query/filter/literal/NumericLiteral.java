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

package org.citydb.query.filter.literal;

import org.citydb.query.filter.common.FilterVisitor;
import org.citydb.query.filter.common.ScalarExpression;
import org.citydb.query.filter.common.Sign;
import org.citydb.query.filter.operation.NumericExpression;

import java.util.Optional;

public class NumericLiteral extends Literal<Number> implements NumericExpression, ScalarExpression {

    private NumericLiteral(Number value) {
        super(value);
    }

    public static NumericLiteral of(Number value) {
        return new NumericLiteral(value);
    }

    public static NumericLiteral of(Object value, Number defaultValue) {
        return of(value).orElse(new NumericLiteral(defaultValue));
    }

    public static Optional<NumericLiteral> of(Object value) {
        if (value instanceof Number number) {
            return Optional.of(new NumericLiteral(number));
        } else {
            try {
                return Optional.of(new NumericLiteral(Double.parseDouble(String.valueOf(value))));
            } catch (Exception e) {
                return Optional.empty();
            }
        }
    }

    public boolean isInteger() {
        return value.longValue() == value.doubleValue();
    }

    public long intValue() {
        return value.longValue();
    }

    public boolean isFloatingPoint() {
        return !isInteger();
    }

    public double doubleValue() {
        return value.doubleValue();
    }

    @Override
    public Sign getSign() {
        return value.doubleValue() < 0 ?
                Sign.MINUS :
                Sign.PLUS;
    }

    @Override
    public NumericExpression negate() {
        value = isInteger() ?
                -value.intValue() :
                -value.doubleValue();
        return this;
    }

    @Override
    public void accept(FilterVisitor visitor) {
        visitor.visit(this);
    }
}
