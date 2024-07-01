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

import java.util.*;

public enum TextToken {
    SINGLE_QUOTE("'"),
    DOUBLE_QUOTE("\""),
    L_PAREN("("),
    R_PAREN(")"),
    L_BRACKET("["),
    R_BRACKET("]"),
    COMMA(","),
    SEMICOLON(";"),
    EQUAL_TO("="),
    NOT_EQUAL_TO("<>"),
    LESS_THAN("<"),
    LESS_THAN_OR_EQUAL_TO("<="),
    GREATER_THAN(">"),
    GREATER_THAN_OR_EQUAL_TO(">="),
    PLUS("+"),
    MINUS("-"),
    SLASH("/"),
    ASTERISK("*"),
    PERCENT("%"),
    CARET("^"),
    DIV("DIV"),
    TRUE("TRUE"),
    FALSE("FALSE"),
    DATE("DATE"),
    TIMESTAMP("TIMESTAMP"),
    INTERVAL("INTERVAL"),
    BBOX("BBOX"),
    POINT("POINT"),
    LINESTRING("LINESTRING"),
    POLYGON("POLYGON"),
    MULTIPOINT("MULTIPOINT"),
    MULTILINESTRING("MULTILINESTRING"),
    MULTIPOLYGON("MULTIPOLYGON"),
    GEOMETRYCOLLECTION("GEOMETRYCOLLECTION"),
    AND("AND"),
    OR("OR"),
    NOT("NOT"),
    LIKE("LIKE"),
    NOT_LIKE("NOT LIKE"),
    BETWEEN("BETWEEN"),
    NOT_BETWEEN("NOT BETWEEN"),
    IN("IN"),
    NOT_IN("NOT IN"),
    IS("IS"),
    NULL("NULL"),
    IS_NULL("IS NULL"),
    IS_NOT_NULL("IS NOT NULL"),
    S_INTERSECTS("S_INTERSECTS"),
    S_EQUALS("S_EQUALS"),
    S_DISJOINT("S_DISJOINT"),
    S_TOUCHES("S_TOUCHES"),
    S_WITHIN("S_WITHIN"),
    S_OVERLAPS("S_OVERLAPS"),
    S_CROSSES("S_CROSSES"),
    S_CONTAINS("S_CONTAINS"),
    S_DWITHIN("S_DWITHIN"),
    S_BEYOND("S_BEYOND"),
    A_EQUALS("A_EQUALS"),
    A_CONTAINS("A_CONTAINS"),
    A_CONTAINEDBY("A_CONTAINEDBY"),
    A_OVERLAPS("A_OVERLAPS"),
    T_AFTER("T_AFTER"),
    T_BEFORE("T_BEFORE"),
    T_CONTAINS("T_CONTAINS"),
    T_DISJOINT("T_DISJOINT"),
    T_DURING("T_DURING"),
    T_EQUALS("T_EQUALS"),
    T_FINISHEDBY("T_FINISHEDBY"),
    T_FINISHES("T_FINISHES"),
    T_INTERSECTS("T_INTERSECTS"),
    T_MEETS("T_MEETS"),
    T_METBY("T_METBY"),
    T_OVERLAPPEDBY("T_OVERLAPPEDBY"),
    T_OVERLAPS("T_OVERLAPS"),
    T_STARTEDBY("T_STARTEDBY"),
    T_STARTS("T_STARTS"),
    SQL("SQL"),
    IDENTIFIER(""),
    EOF(""),
    UNDEFINED("");

    static final EnumSet<TextToken> QUOTE_CHARS = EnumSet.of(SINGLE_QUOTE, DOUBLE_QUOTE);
    static final EnumSet<TextToken> SYNTAX_CHARS = EnumSet.of(L_PAREN, R_PAREN, L_BRACKET, R_BRACKET, COMMA,
            SEMICOLON, EQUAL_TO, LESS_THAN, GREATER_THAN, PLUS, MINUS, SLASH, ASTERISK, PERCENT, CARET);
    static final EnumSet<TextToken> ARITHMETIC_OPERATORS = EnumSet.of(PLUS, MINUS, SLASH, DIV, ASTERISK, PERCENT,
            CARET);
    static final EnumSet<TextToken> ARRAY_OPERATORS = EnumSet.of(A_EQUALS, A_CONTAINS, A_CONTAINEDBY, A_OVERLAPS);
    static final EnumSet<TextToken> BINARY_BOOLEAN_OPERATORS = EnumSet.of(AND, OR);
    static final EnumSet<TextToken> UNARY_COMPARISON_OPERATORS = EnumSet.of(LIKE, NOT_LIKE, BETWEEN, NOT_BETWEEN,
            IN, NOT_IN, IS_NULL, IS_NOT_NULL);
    static final EnumSet<TextToken> BINARY_COMPARISON_OPERATORS = EnumSet.of(EQUAL_TO, NOT_EQUAL_TO, LESS_THAN,
            LESS_THAN_OR_EQUAL_TO, GREATER_THAN, GREATER_THAN_OR_EQUAL_TO);
    static final EnumSet<TextToken> BINARY_SPATIAL_OPERATORS = EnumSet.of(S_INTERSECTS, S_EQUALS, S_DISJOINT, S_TOUCHES,
            S_WITHIN, S_OVERLAPS, S_CROSSES, S_CONTAINS);
    static final EnumSet<TextToken> SPATIAL_DISTANCE_OPERATORS = EnumSet.of(S_DWITHIN, S_BEYOND);
    static final EnumSet<TextToken> TEMPORAL_OPERATORS = EnumSet.of(T_AFTER, T_BEFORE, T_CONTAINS, T_DISJOINT,
            T_DURING, T_EQUALS, T_FINISHEDBY, T_FINISHES, T_INTERSECTS, T_MEETS, T_METBY, T_OVERLAPPEDBY, T_OVERLAPS,
            T_STARTEDBY, T_STARTS);
    static final EnumSet<TextToken> GEOMETRIES = EnumSet.of(BBOX, POINT, LINESTRING, POLYGON, MULTIPOINT,
            MULTILINESTRING, MULTIPOLYGON, GEOMETRYCOLLECTION);

    private final static Map<String, TextToken> tokens = new HashMap<>();
    private final String token;

    TextToken(String token) {
        this.token = token;
    }

    static {
        Arrays.stream(values()).forEach(type -> tokens.put(type.token, type));
    }

    static TextToken of(String token) {
        return of(token, UNDEFINED);
    }

    static TextToken of(String token, TextToken defaultValue) {
        return token != null ?
                tokens.getOrDefault(token.toUpperCase(Locale.ROOT), defaultValue) :
                defaultValue;
    }

    String value() {
        return token;
    }

    @Override
    public String toString() {
        return token;
    }
}
