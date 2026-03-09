/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter.operation;

import org.citydb.query.filter.encoding.JSONToken;
import org.citydb.query.filter.encoding.TextToken;

import java.util.EnumSet;

public enum BooleanOperator {
    AND(JSONToken.AND, TextToken.AND),
    OR(JSONToken.OR, TextToken.OR),
    NOT(JSONToken.NOT, TextToken.NOT);

    public static final EnumSet<BooleanOperator> BINARY_OPERATORS = EnumSet.of(AND, OR);

    private final JSONToken jsonToken;
    private final TextToken textToken;

    BooleanOperator(JSONToken jsonToken, TextToken textToken) {
        this.jsonToken = jsonToken;
        this.textToken = textToken;
    }

    public static BooleanOperator of(JSONToken token) {
        for (BooleanOperator operator : values()) {
            if (operator.jsonToken == token) {
                return operator;
            }
        }

        return null;
    }

    public static BooleanOperator of(TextToken token) {
        for (BooleanOperator operator : values()) {
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
}
