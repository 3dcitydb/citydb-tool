/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter.encoding;

import org.citydb.query.filter.Filter;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

public class FilterConfigWriter extends ValueSerializer<Filter> {
    @Override
    public void serialize(Filter filter, JsonGenerator generator, SerializationContext context) {
        FilterJSONWriter.newInstance().write(filter.getExpression(), generator);
    }
}
