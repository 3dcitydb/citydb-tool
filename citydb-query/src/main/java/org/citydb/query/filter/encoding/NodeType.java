/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter.encoding;

import java.util.EnumSet;

public enum NodeType {
    BOOLEAN_LITERAL,
    STRING_LITERAL,
    TIME_INSTANT_LITERAL,
    TIME_INTERVAL,
    GEOMETRY_LITERAL,
    IDENTIFIER,
    PROPERTY_REF,
    ARITHMETIC_EXPRESSION,
    BOOLEAN_PREDICATE,
    COMPARISON_PREDICATE,
    SPATIAL_PREDICATE,
    TEMPORAL_PREDICATE,
    ARRAY_PREDICATE,
    SQL_PREDICATE,
    FUNCTION,
    ARRAY,
    SYNTAX,
    EMPTY;

    public static final EnumSet<NodeType> LITERALS = EnumSet.of(BOOLEAN_LITERAL, STRING_LITERAL, TIME_INSTANT_LITERAL,
            PROPERTY_REF, IDENTIFIER);
}
