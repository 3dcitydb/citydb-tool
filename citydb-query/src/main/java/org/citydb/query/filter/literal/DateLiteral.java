/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter.literal;

import org.citydb.query.filter.common.FilterVisitor;
import org.citydb.query.filter.common.ScalarExpression;

import java.time.LocalDate;
import java.util.Optional;

public class DateLiteral extends Literal<LocalDate> implements ScalarExpression {

    private DateLiteral(LocalDate value) {
        super(value);
    }

    public static DateLiteral of(LocalDate value) {
        return new DateLiteral(value);
    }

    public static DateLiteral of(Object value, LocalDate defaultValue) {
        return of(value).orElse(new DateLiteral(defaultValue));
    }

    public static Optional<DateLiteral> of(Object value) {
        if (value instanceof LocalDate localDate) {
            return Optional.of(new DateLiteral(localDate));
        } else {
            try {
                return Optional.of(new DateLiteral(LocalDate.parse(String.valueOf(value))));
            } catch (Exception e) {
                return Optional.empty();
            }
        }
    }

    @Override
    public void accept(FilterVisitor visitor) {
        visitor.visit(this);
    }
}
