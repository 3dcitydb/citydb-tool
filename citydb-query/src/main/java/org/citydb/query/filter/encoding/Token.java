/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter.encoding;

import java.util.Objects;

public class Token {
    static final Token EOF = new Token(TextToken.EOF, TextToken.EOF.value());
    static final Token UNDEFINED = new Token(TextToken.UNDEFINED, TextToken.UNDEFINED.value());
    private final TextToken type;
    private String value;

    private Token(TextToken type, String value) {
        this.type = Objects.requireNonNull(type, "The token type must not be null.");
        this.value = value;
    }

    static Token of(TextToken type, String value) {
        return new Token(type, value);
    }

    static Token of(TextToken type) {
        return new Token(type, type.value());
    }

    TextToken getType() {
        return type;
    }

    String getValue() {
        return value;
    }

    Token setValue(String value) {
        this.value = value;
        return this;
    }

    Precedence getPrecedence() {
        return Precedence.of(type);
    }

    @Override
    public String toString() {
        return value;
    }
}
