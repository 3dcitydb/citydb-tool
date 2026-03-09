/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
