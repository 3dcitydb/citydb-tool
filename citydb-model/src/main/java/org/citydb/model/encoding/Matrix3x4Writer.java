/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.encoding;

import org.citydb.model.common.Matrix3x4;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

public class Matrix3x4Writer extends ValueSerializer<Matrix3x4> {
    @Override
    public void serialize(Matrix3x4 matrix, JsonGenerator generator, SerializationContext context) {
        generator.writeStartArray();
        for (Double value : matrix.toRowMajor()) {
            generator.writeNumber(value);
        }

        generator.writeEndArray();
    }
}
