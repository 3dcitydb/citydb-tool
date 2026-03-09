/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
    public static final OffsetDateTime LOCAL_TIME_BASE_DATE = OffsetDateTime.of(
            LocalDateTime.of(1, 1, 1, 0, 0, 0), ZoneOffset.UTC);

    public static OffsetDateTime toDateTime(LocalTime time) {
        return toDateTime(time.atOffset(ZoneOffset.UTC));
    }

    public static OffsetDateTime toDateTime(OffsetTime time) {
        time = time.withOffsetSameInstant(ZoneOffset.UTC);
        return LOCAL_TIME_BASE_DATE.plusHours(time.getHour())
                .plusMinutes(time.getMinute())
                .plusSeconds(time.getSecond())
                .plusNanos(time.getNano());
    }

    public static OffsetDateTime toDateTime(LocalDate date) {
        return toDateTime(date.atStartOfDay());
    }

    public static OffsetDateTime toDateTime(LocalDateTime dateTime) {
        return dateTime.atOffset(ZoneOffset.UTC);
    }

    public static OffsetDateTime toDateTime(Duration duration) {
        return LOCAL_TIME_BASE_DATE.plus(duration);
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
