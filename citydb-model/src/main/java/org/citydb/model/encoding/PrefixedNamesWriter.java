/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.encoding;

import org.citydb.model.common.Namespaces;
import org.citydb.model.common.PrefixedName;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import java.util.List;

public class PrefixedNamesWriter extends ValueSerializer<List<PrefixedName>> {
    @Override
    public void serialize(List<PrefixedName> names, JsonGenerator generator, SerializationContext context) {
        generator.writeStartArray();
        for (PrefixedName name : names) {
            generator.writeStartObject();
            generator.writeStringProperty("name", name.getPrefix()
                    .map(prefix -> prefix + ":" + name.getLocalName())
                    .orElse(name.getLocalName()));
            if (!name.getNamespace().equals(Namespaces.EMPTY_NAMESPACE)) {
                generator.writeStringProperty("namespace", name.getNamespace());
            }

            generator.writeEndObject();
        }

        generator.writeEndArray();
    }
}
