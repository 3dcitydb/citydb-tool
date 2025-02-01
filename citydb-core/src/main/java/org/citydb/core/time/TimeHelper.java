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

package org.citydb.core.time;

import java.time.*;
import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;

import static java.time.temporal.ChronoField.*;

public class TimeHelper {
    public static final DateTimeFormatter DATE_TIME_FORMATTER = dateTimeFormatter(0, 0, 0);
    public static final DateTimeFormatter VALIDITY_TIME_FORMATTER = dateTimeFormatter(23, 59, 59);

    public static OffsetDateTime toDateTime(LocalDate date) {
        return date.atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    public static OffsetDateTime toDateTime(LocalDateTime dateTime) {
        return dateTime.atOffset(ZoneOffset.UTC);
    }

    public static OffsetDateTime toValidityTime(LocalDate date) {
        return toDateTime(date.atTime(LocalTime.MAX));
    }

    public static OffsetDateTime toValidityTime(LocalDateTime dateTime) {
        return toDateTime(dateTime);
    }

    private static DateTimeFormatter dateTimeFormatter(int hourOfDay, int minuteOfHour, int secondOfMinute) {
        return new DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .append(DateTimeFormatter.ISO_LOCAL_DATE)
                .optionalStart()
                .appendLiteral('T')
                .append(DateTimeFormatter.ISO_LOCAL_TIME)
                .optionalStart()
                .appendOffsetId()
                .optionalEnd()
                .optionalEnd()
                .parseDefaulting(HOUR_OF_DAY, hourOfDay)
                .parseDefaulting(MINUTE_OF_HOUR, minuteOfHour)
                .parseDefaulting(SECOND_OF_MINUTE, secondOfMinute)
                .parseDefaulting(OFFSET_SECONDS, 0)
                .toFormatter()
                .withResolverStyle(ResolverStyle.STRICT)
                .withChronology(IsoChronology.INSTANCE);
    }
}
