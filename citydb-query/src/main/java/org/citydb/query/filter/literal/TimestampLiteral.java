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
