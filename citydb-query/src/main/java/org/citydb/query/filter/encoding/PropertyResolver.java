/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter.encoding;

import org.citydb.query.filter.common.Expression;
import org.citydb.query.filter.literal.PropertyRef;

import java.util.Optional;

@FunctionalInterface
public interface PropertyResolver {
    Optional<Expression> resolve(PropertyRef propertyRef, PropertyRef step) throws FilterParseException;
}
