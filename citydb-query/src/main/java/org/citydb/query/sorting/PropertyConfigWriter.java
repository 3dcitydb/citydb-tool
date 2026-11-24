/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.sorting;

import org.citydb.query.filter.encoding.FilterTextWriter;
import org.citydb.query.filter.literal.PropertyRef;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

public class PropertyConfigWriter extends ValueSerializer<PropertyRef> {
    @Override
    public void serialize(PropertyRef propertyRef, JsonGenerator generator, SerializationContext context) {
        generator.writeString(FilterTextWriter.newInstance().write(propertyRef));
    }
}
