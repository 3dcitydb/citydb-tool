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
}
