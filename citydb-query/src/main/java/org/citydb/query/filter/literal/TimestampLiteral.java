/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter.literal;

import org.citydb.query.filter.common.FilterVisitor;
import org.citydb.query.filter.common.ScalarExpression;

import java.time.OffsetDateTime;
import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.util.Optional;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;
import static java.time.temporal.ChronoField.OFFSET_SECONDS;

public class TimestampLiteral extends Literal<OffsetDateTime> implements ScalarExpression {
    public static final DateTimeFormatter OFFSET_DATE_TIME = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .append(ISO_LOCAL_DATE_TIME)
            .optionalStart()
            .parseLenient()
            .appendOffsetId()
            .parseStrict()
            .optionalEnd()
            .parseDefaulting(OFFSET_SECONDS, 0)
            .toFormatter()
            .withResolverStyle(ResolverStyle.STRICT)
            .withChronology(IsoChronology.INSTANCE);

    private TimestampLiteral(OffsetDateTime value) {
        super(value);
    }

    public static TimestampLiteral of(OffsetDateTime value) {
        return new TimestampLiteral(value);
    }

    public static TimestampLiteral of(Object value, OffsetDateTime defaultValue) {
        return of(value).orElse(new TimestampLiteral(defaultValue));
    }

    public static Optional<TimestampLiteral> of(Object value) {
        if (value instanceof OffsetDateTime offsetDateTime) {
            return Optional.of(new TimestampLiteral(offsetDateTime));
        } else {
            try {
                return Optional.of(new TimestampLiteral(
                        OffsetDateTime.parse(String.valueOf(value), OFFSET_DATE_TIME)));
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
