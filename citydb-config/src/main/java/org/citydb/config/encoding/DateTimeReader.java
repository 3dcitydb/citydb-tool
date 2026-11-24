/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.config.encoding;

import org.citydb.core.time.TimeHelper;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

public class DateTimeReader extends ValueDeserializer<OffsetDateTime> {
    @Override
    public OffsetDateTime deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            return context.reportInputMismatch(this, "Expected a date-time string.");
        }

        String value = parser.getString();
        try {
            return OffsetDateTime.parse(value, TimeHelper.DATE_TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            return context.reportInputMismatch(this, "Invalid date-time '%s'.", value);
        }
    }
}
