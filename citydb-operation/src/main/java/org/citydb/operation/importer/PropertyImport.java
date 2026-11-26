/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.importer;

import org.citydb.model.property.Property;

import java.util.Objects;

public record PropertyImport(Property<?> property, long featureId) {
    public PropertyImport {
        Objects.requireNonNull(property, "The property must not be null.");
    }
}
