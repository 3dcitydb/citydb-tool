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
