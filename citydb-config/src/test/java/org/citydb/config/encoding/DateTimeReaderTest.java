/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.config.encoding;

import org.citydb.core.json.JsonMapping;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DateTimeReaderTest {

    public static class Holder {
        @JsonDeserialize(using = DateTimeReader.class)
        private OffsetDateTime date;
        @JsonDeserialize(using = ValidityTimeReader.class)
        private OffsetDateTime validity;
    }

    @Test
    public void dateTimeReaderAcceptsCliFormats() {
        assertEquals(OffsetDateTime.of(2025, 1, 2, 3, 4, 5, 0, ZoneOffset.ofHours(2)),
                read("{\"date\": \"2025-01-02T03:04:05+02:00\"}").date);
        assertEquals(OffsetDateTime.of(2025, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC),
                read("{\"date\": \"2025-01-02T03:04:05\"}").date);
        assertEquals(OffsetDateTime.of(2025, 1, 2, 0, 0, 0, 0, ZoneOffset.UTC),
                read("{\"date\": \"2025-01-02\"}").date);
        assertThrows(JacksonException.class, () -> read("{\"date\": \"not a date\"}"));
    }

    @Test
    public void validityTimeReaderDefaultsToEndOfDay() {
        assertEquals(OffsetDateTime.of(2025, 1, 2, 23, 59, 59, 0, ZoneOffset.UTC),
                read("{\"validity\": \"2025-01-02\"}").validity);
    }

    private Holder read(String json) {
        return JsonMapping.mapper().readValue(json, Holder.class);
    }
}
