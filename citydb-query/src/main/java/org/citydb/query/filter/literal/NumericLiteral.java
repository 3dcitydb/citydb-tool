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

package org.citydb.query.filter.literal;

import org.citydb.query.filter.common.FilterVisitor;
import org.citydb.query.filter.common.ScalarExpression;
import org.citydb.query.filter.common.Sign;
import org.citydb.query.filter.operation.NumericExpression;

import java.util.Optional;

public class NumericLiteral extends Literal<Number> implements NumericExpression, ScalarExpression {
    private final boolean isInteger;

    private NumericLiteral(long value) {
        super(value);
        this.isInteger = true;
    }

    private NumericLiteral(double value) {
        super(value);
        this.isInteger = false;
    }

    public static NumericLiteral of(Number value) {
        if (value instanceof Long
                || value instanceof Integer
                || value instanceof Short
                || value instanceof Byte) {
            return new NumericLiteral(value.longValue());
        } else if (value instanceof Double
                || value instanceof Float) {
            return new NumericLiteral(value.doubleValue());
        } else {
            try {
                return new NumericLiteral(Long.parseLong(String.valueOf(value)));
            } catch (NumberFormatException e) {
                return new NumericLiteral(value.doubleValue());
            }
        }
    }

    public static NumericLiteral of(Object value, Number defaultValue) {
        return of(value).orElse(of(defaultValue));
    }

    public static Optional<NumericLiteral> of(Object value) {
        if (value instanceof Number number) {
            return Optional.of(of(number));
        } else {
            String number = String.valueOf(value);
            try {
                return Optional.of(new NumericLiteral(Long.parseLong(number)));
            } catch (NumberFormatException e) {
                //
            }

            try {
                return Optional.of(new NumericLiteral(Double.parseDouble(number)));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
    }

    public boolean isInteger() {
        return isInteger;
    }

    public long intValue() {
        return value.longValue();
    }

    public boolean isFloatingPoint() {
        return !isInteger;
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
        value = isInteger ?
                -value.intValue() :
                -value.doubleValue();
        return this;
    }

    @Override
    public void accept(FilterVisitor visitor) {
        visitor.visit(this);
    }
}
