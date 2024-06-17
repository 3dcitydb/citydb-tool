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

public enum ComparisonOperator {
    EQUAL_TO(JSONToken.EQUAL_TO, TextToken.EQUAL_TO),
    NOT_EQUAL_TO(JSONToken.NOT_EQUAL_TO, TextToken.NOT_EQUAL_TO),
    LESS_THAN(JSONToken.LESS_THAN, TextToken.LESS_THAN),
    LESS_THAN_OR_EQUAL_TO(JSONToken.LESS_THAN_OR_EQUAL_TO, TextToken.LESS_THAN_OR_EQUAL_TO),
    GREATER_THAN(JSONToken.GREATER_THAN, TextToken.GREATER_THAN),
    GREATER_THAN_OR_EQUAL_TO(JSONToken.GREATER_THAN_OR_EQUAL_TO, TextToken.GREATER_THAN_OR_EQUAL_TO),
    IS_NULL(JSONToken.IS_NULL, TextToken.IS_NULL),
    IS_NOT_NULL(JSONToken.UNDEFINED, TextToken.IS_NOT_NULL),
    LIKE(JSONToken.LIKE, TextToken.LIKE),
    NOT_LIKE(JSONToken.UNDEFINED, TextToken.NOT_LIKE),
    BETWEEN(JSONToken.BETWEEN, TextToken.BETWEEN),
    NOT_BETWEEN(JSONToken.UNDEFINED, TextToken.NOT_BETWEEN),
    IN(JSONToken.IN, TextToken.IN),
    NOT_IN(JSONToken.UNDEFINED, TextToken.NOT_IN);

    public static final EnumSet<ComparisonOperator> BINARY_OPERATORS = EnumSet.of(EQUAL_TO, NOT_EQUAL_TO,
            LESS_THAN, LESS_THAN_OR_EQUAL_TO, GREATER_THAN, GREATER_THAN_OR_EQUAL_TO);

    private final JSONToken jsonToken;
    private final TextToken textToken;

    ComparisonOperator(JSONToken jsonToken, TextToken textToken) {
        this.jsonToken = jsonToken;
        this.textToken = textToken;
    }

    public static ComparisonOperator of(JSONToken token) {
        for (ComparisonOperator operator : values()) {
            if (operator.jsonToken == token) {
                return operator;
            }
        }

        return null;
    }

    public static ComparisonOperator of(TextToken token) {
        for (ComparisonOperator operator : values()) {
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

    public ComparisonOperator negate() {
        return switch (this) {
            case EQUAL_TO -> NOT_EQUAL_TO;
            case NOT_EQUAL_TO -> EQUAL_TO;
            case LESS_THAN -> GREATER_THAN_OR_EQUAL_TO;
            case LESS_THAN_OR_EQUAL_TO -> GREATER_THAN;
            case GREATER_THAN -> LESS_THAN_OR_EQUAL_TO;
            case GREATER_THAN_OR_EQUAL_TO -> LESS_THAN;
            case IS_NULL -> IS_NOT_NULL;
            case IS_NOT_NULL -> IS_NULL;
            case LIKE -> NOT_LIKE;
            case NOT_LIKE -> LIKE;
            case BETWEEN -> NOT_BETWEEN;
            case NOT_BETWEEN -> BETWEEN;
            case IN -> NOT_IN;
            case NOT_IN -> IN;
        };
    }

    @Override
    public String toString() {
        return jsonToken.toString();
    }
}
