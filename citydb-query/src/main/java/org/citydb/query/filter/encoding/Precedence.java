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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public enum Precedence {
    EXPONENTIATION(TextToken.CARET, 1),
    MULTIPLICATION(TextToken.ASTERISK, 2),
    DIVISION(TextToken.SLASH, 2),
    INT_DIVISION(TextToken.DIV, 2),
    MODULO(TextToken.PERCENT, 2),
    ADDITION(TextToken.PLUS, 3),
    SUBTRACTION(TextToken.MINUS, 3),
    EQUAL_TO(TextToken.EQUAL_TO, 4),
    NOT_EQUAL_TO(TextToken.NOT_EQUAL_TO, 4),
    LESS_THAN(TextToken.LESS_THAN, 4),
    LESS_THAN_OR_EQUAL_TO(TextToken.LESS_THAN_OR_EQUAL_TO, 4),
    GREATER_THAN(TextToken.GREATER_THAN, 4),
    GREATER_THAN_OR_EQUAL_TO(TextToken.GREATER_THAN_OR_EQUAL_TO, 4),
    IS_NULL(TextToken.IS_NULL, 4),
    IS_NOT_NULL(TextToken.IS_NOT_NULL, 4),
    LIKE(TextToken.LIKE, 4),
    IN(TextToken.IN, 4),
    BETWEEN(TextToken.BETWEEN, 5),
    NOT(TextToken.NOT, 6),
    AND(TextToken.AND, 7),
    OR(TextToken.OR, 8),
    UNDEFINED(null, Integer.MAX_VALUE);

    private final static Map<TextToken, Precedence> values = new HashMap<>();
    private final TextToken token;
    private final int level;

    Precedence(TextToken token, int level) {
        this.token = token;
        this.level = level;
    }

    static {
        Arrays.stream(values()).forEach(precedence -> values.put(precedence.token, precedence));
    }

    static Precedence of(TextToken token) {
        return token != null ?
                values.getOrDefault(token, Precedence.UNDEFINED) :
                Precedence.UNDEFINED;
    }

    boolean isHigher(Precedence precedence) {
        return precedence == null || this.level < precedence.level;
    }
}
