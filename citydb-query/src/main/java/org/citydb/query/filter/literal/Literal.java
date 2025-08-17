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

import org.citydb.query.filter.common.CharacterExpression;
import org.citydb.query.filter.common.Expression;
import org.citydb.query.filter.common.PatternExpression;
import org.citydb.query.filter.common.ScalarExpression;
import org.citydb.query.filter.operation.BooleanExpression;
import org.citydb.query.filter.operation.NumericExpression;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

public abstract class Literal<T> implements Expression {
    protected T value;

    protected Literal(T value) {
        this.value = Objects.requireNonNull(value, "The literal value must not be null.");
    }

    public static ScalarExpression ofScalar(Object value) {
        if (value instanceof ScalarExpression expression) {
            return expression;
        } else if (value instanceof String stringValue) {
            return StringLiteral.of(stringValue);
        } else if (value instanceof Number number) {
            return NumericLiteral.of(number);
        } else if (value instanceof Boolean bool) {
            return BooleanLiteral.of(bool);
        } else if (value instanceof LocalDate localDate) {
            return DateLiteral.of(localDate);
        } else if (value instanceof OffsetDateTime offsetDateTime) {
            return TimestampLiteral.of(offsetDateTime);
        } else {
            return StringLiteral.of(value);
        }
    }

    public T getValue() {
        return value;
    }

    public <E extends Expression> Optional<E> cast(Class<E> type) {
        if (BooleanExpression.class.isAssignableFrom(type)) {
            return Optional.of(type.cast(BooleanLiteral.of(value)));
        } else if (NumericExpression.class.isAssignableFrom(type)) {
            return NumericLiteral.of(value)
                    .filter(type::isInstance)
                    .map(type::cast);
        } else if (CharacterExpression.class.isAssignableFrom(type)
                || PatternExpression.class.isAssignableFrom(type)) {
            return Optional.of(type.cast(StringLiteral.of(value)));
        } else if (TimestampLiteral.class.isAssignableFrom(type)) {
            return TimestampLiteral.of(value)
                    .filter(type::isInstance)
                    .map(type::cast);
        } else if (DateLiteral.class.isAssignableFrom(type)) {
            return DateLiteral.of(value)
                    .filter(type::isInstance)
                    .map(type::cast);
        } else {
            return Optional.empty();
        }
    }
}
