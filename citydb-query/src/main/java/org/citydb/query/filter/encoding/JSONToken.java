/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
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

import java.util.*;

public enum JSONToken {
    EQUAL_TO("="),
    NOT_EQUAL_TO("<>"),
    LESS_THAN("<"),
    LESS_THAN_OR_EQUAL_TO("<="),
    GREATER_THAN(">"),
    GREATER_THAN_OR_EQUAL_TO(">="),
    PLUS("+"),
    MINUS("-"),
    DIVIDE("/"),
    INT_DIVIDE("div"),
    MULTIPLY("*"),
    MODULO("%"),
    POWER("^"),
    DATE("date"),
    TIMESTAMP("timestamp"),
    INTERVAL("interval"),
    BBOX("bbox"),
    POINT("Point"),
    LINESTRING("LineString"),
    POLYGON("Polygon"),
    MULTIPOINT("MultiPoint"),
    MULTILINESTRING("MultiLineString"),
    MULTIPOLYGON("MultiPolygon"),
    GEOMETRYCOLLECTION("GeometryCollection"),
    TYPE("type"),
    PROPERTY("property"),
    COORDINATES("coordinates"),
    AND("and"),
    OR("or"),
    NOT("not"),
    LIKE("like"),
    BETWEEN("between"),
    IN("in"),
    IS_NULL("isNull"),
    S_INTERSECTS("s_intersects"),
    S_EQUALS("s_equals"),
    S_DISJOINT("s_disjoint"),
    S_TOUCHES("s_touches"),
    S_WITHIN("s_within"),
    S_OVERLAPS("s_overlaps"),
    S_CROSSES("s_crosses"),
    S_CONTAINS("s_contains"),
    S_DWITHIN("s_dWithin"),
    S_BEYOND("s_beyond"),
    A_EQUALS("a_equals"),
    A_CONTAINS("a_contains"),
    A_CONTAINEDBY("a_containedBy"),
    A_OVERLAPS("a_overlaps"),
    T_AFTER("t_after"),
    T_BEFORE("t_before"),
    T_CONTAINS("t_contains"),
    T_DISJOINT("t_disjoint"),
    T_DURING("t_during"),
    T_EQUALS("t_equals"),
    T_FINISHEDBY("t_finishedBy"),
    T_FINISHES("t_finishes"),
    T_INTERSECTS("t_intersects"),
    T_MEETS("t_meets"),
    T_METBY("t_metBy"),
    T_OVERLAPPEDBY("t_overlappedBy"),
    T_OVERLAPS("t_overlaps"),
    T_STARTEDBY("t_startedBy"),
    T_STARTS("t_starts"),
    SQL("sql"),
    OP("op"),
    ARGS("args"),
    UNDEFINED("");

    static final EnumSet<JSONToken> ARITHMETIC_OPERATORS = EnumSet.of(PLUS, MINUS, DIVIDE, INT_DIVIDE, MULTIPLY,
            MODULO, POWER);
    static final EnumSet<JSONToken> ARRAY_OPERATORS = EnumSet.of(A_EQUALS, A_CONTAINS, A_CONTAINEDBY, A_OVERLAPS);
    static final EnumSet<JSONToken> BINARY_BOOLEAN_OPERATORS = EnumSet.of(AND, OR);
    static final EnumSet<JSONToken> BINARY_COMPARISON_OPERATORS = EnumSet.of(EQUAL_TO, NOT_EQUAL_TO, LESS_THAN,
            LESS_THAN_OR_EQUAL_TO, GREATER_THAN, GREATER_THAN_OR_EQUAL_TO);
    static final EnumSet<JSONToken> BINARY_SPATIAL_OPERATORS = EnumSet.of(S_INTERSECTS, S_EQUALS, S_DISJOINT, S_TOUCHES,
            S_WITHIN, S_OVERLAPS, S_CROSSES, S_CONTAINS);
    static final EnumSet<JSONToken> SPATIAL_DISTANCE_OPERATORS = EnumSet.of(S_DWITHIN, S_BEYOND);
    static final EnumSet<JSONToken> TEMPORAL_OPERATORS = EnumSet.of(T_AFTER, T_BEFORE, T_CONTAINS, T_DISJOINT,
            T_DURING, T_EQUALS, T_FINISHEDBY, T_FINISHES, T_INTERSECTS, T_MEETS, T_METBY, T_OVERLAPPEDBY, T_OVERLAPS,
            T_STARTEDBY, T_STARTS);
    static final EnumSet<JSONToken> GEOMETRIES = EnumSet.of(BBOX, POINT, LINESTRING, POLYGON, MULTIPOINT,
            MULTILINESTRING, MULTIPOLYGON, GEOMETRYCOLLECTION);

    private final static Map<String, JSONToken> keywords = new HashMap<>();
    private final String token;

    JSONToken(String token) {
        this.token = token;
    }

    static {
        Arrays.stream(values()).forEach(type -> keywords.put(type.token.toUpperCase(Locale.ROOT), type));
    }

    static JSONToken of(String token) {
        return token != null ?
                keywords.getOrDefault(token.toUpperCase(Locale.ROOT), UNDEFINED) :
                UNDEFINED;
    }

    public String value() {
        return token;
    }

    @Override
    public String toString() {
        return token;
    }
}
