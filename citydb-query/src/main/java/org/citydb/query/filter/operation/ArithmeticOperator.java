/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter.operation;

import org.citydb.query.filter.encoding.JSONToken;
import org.citydb.query.filter.encoding.TextToken;

public enum ArithmeticOperator {
    POWER(1, JSONToken.POWER, TextToken.CARET),
    MULTIPLY(2, JSONToken.MULTIPLY, TextToken.ASTERISK),
    DIVIDE(2, JSONToken.DIVIDE, TextToken.SLASH),
    INT_DIVIDE(2, JSONToken.INT_DIVIDE, TextToken.DIV),
    MODULO(2, JSONToken.MODULO, TextToken.PERCENT),
    PLUS(3, JSONToken.PLUS, TextToken.PLUS),
    MINUS(3, JSONToken.MINUS, TextToken.MINUS);

    private final int precedence;
    private final JSONToken jsonToken;
    private final TextToken textToken;

    ArithmeticOperator(int precedence, JSONToken jsonToken, TextToken textToken) {
        this.precedence = precedence;
        this.jsonToken = jsonToken;
        this.textToken = textToken;
    }

    public static ArithmeticOperator of(JSONToken token) {
        for (ArithmeticOperator operator : values()) {
            if (operator.jsonToken == token) {
                return operator;
            }
        }

        return null;
    }

    public static ArithmeticOperator of(TextToken token) {
        for (ArithmeticOperator operator : values()) {
            if (operator.textToken == token) {
                return operator;
            }
        }

        return null;
    }

    public JSONToken getJSONToken() {
        return jsonToken;
    }

    public TextToken getTextToken() {
        return textToken;
    }

    public boolean takesPrecedenceOver(ArithmeticOperator other) {
        return precedence < other.precedence;
    }

    @Override
    public String toString() {
        return jsonToken.toString();
    }
}
