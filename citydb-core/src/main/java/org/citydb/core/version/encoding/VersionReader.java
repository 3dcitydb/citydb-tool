/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.core.version.encoding;

import org.citydb.core.version.Version;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class VersionReader extends ValueDeserializer<Version> {
    @Override
    public Version deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            return context.reportInputMismatch(this, "Expected a version string.");
        }

        String value = parser.getString();
        return Version.parse(value).orElseGet(() ->
                context.reportInputMismatch(this, "Invalid version string '%s'.", value));
    }
}
