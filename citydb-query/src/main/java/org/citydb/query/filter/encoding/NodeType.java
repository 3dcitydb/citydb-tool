/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
 * virtualcitysystems GmbH, Germany
 * https://vc.systems/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
    FUNCTION,
    ARRAY,
    SYNTAX,
    EMPTY;

    public static final EnumSet<NodeType> LITERALS = EnumSet.of(BOOLEAN_LITERAL, STRING_LITERAL, TIME_INSTANT_LITERAL,
            PROPERTY_REF, IDENTIFIER);
}
