/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
