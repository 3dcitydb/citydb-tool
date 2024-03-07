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
