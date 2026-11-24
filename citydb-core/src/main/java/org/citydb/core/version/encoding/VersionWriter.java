/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.core.version.encoding;

import org.citydb.core.version.Version;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

public class VersionWriter extends ValueSerializer<Version> {
    @Override
    public void serialize(Version version, JsonGenerator generator, SerializationContext context) {
        generator.writeString(version.toVersionString());
    }
}
