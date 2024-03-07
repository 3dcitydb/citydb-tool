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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class Node {
    static final Node EMPTY = new Node(NodeType.EMPTY, Token.UNDEFINED);
    private final NodeType type;
    private final Token token;
    private final List<Node> children = new ArrayList<>();
    private boolean isEnclosed;
    private TextToken sign;

    Node(NodeType type, Token token) {
        this.type = Objects.requireNonNull(type, "The node type must not be null.");
        this.token = Objects.requireNonNull(token, "The node token must not be null.");
    }

    static Node of(NodeType type, Token token) {
        return new Node(type, token);
    }

    static Node of(Token token) {
        if (token.getType() == TextToken.SINGLE_QUOTE) {
            return new Node(NodeType.STRING_LITERAL, token);
        } else if (token.getType() == TextToken.DOUBLE_QUOTE) {
            return new Node(NodeType.PROPERTY_REF, token);
        } else if (token.getType() == TextToken.DATE
                || token.getType() == TextToken.TIMESTAMP) {
            return new Node(NodeType.TIME_INSTANT_LITERAL, token);
        } else if (token.getType() == TextToken.INTERVAL) {
            return new Node(NodeType.TIME_INTERVAL, token);
        } else if (token.getType() == TextToken.TRUE
                || token.getType() == TextToken.FALSE) {
            return new Node(NodeType.BOOLEAN_LITERAL, token);
        } else if (TextToken.GEOMETRIES.contains(token.getType())) {
            return new Node(NodeType.GEOMETRY_LITERAL, token);
        } else if (TextToken.ARITHMETIC_OPERATORS.contains(token.getType())) {
            return new Node(NodeType.ARITHMETIC_EXPRESSION, token);
        } else if (TextToken.BINARY_BOOLEAN_OPERATORS.contains(token.getType())
                || token.getType() == TextToken.NOT) {
            return new Node(NodeType.BOOLEAN_PREDICATE, token);
        } else if (TextToken.UNARY_COMPARISON_OPERATORS.contains(token.getType())
                || TextToken.BINARY_COMPARISON_OPERATORS.contains(token.getType())) {
            return new Node(NodeType.COMPARISON_PREDICATE, token);
        } else if (TextToken.SPATIAL_OPERATORS.contains(token.getType())) {
            return new Node(NodeType.SPATIAL_PREDICATE, token);
        } else if (TextToken.TEMPORAL_OPERATORS.contains(token.getType())) {
            return new Node(NodeType.TEMPORAL_PREDICATE, token);
        } else if (TextToken.ARRAY_OPERATORS.contains(token.getType())) {
            return new Node(NodeType.ARRAY_PREDICATE, token);
        } else if (TextToken.SYNTAX_CHARS.contains(token.getType())) {
            return new Node(NodeType.SYNTAX, token);
        } else if (token == Token.EOF) {
            return Node.EMPTY;
        } else {
            return new Node(NodeType.IDENTIFIER, token);
        }
    }

    NodeType getType() {
        return type;
    }

    Token getToken() {
        return token;
    }

    boolean hasChildren() {
        return !children.isEmpty();
    }

    List<Node> getChildren() {
        return children;
    }

    Node addChild(Node child) {
        if (child != Node.EMPTY) {
            children.add(child);
        }

        return this;
    }

    boolean isEnclosed() {
        return isEnclosed;
    }

    Node setEnclosed() {
        isEnclosed = true;
        return this;
    }

    Optional<TextToken> getSign() {
        return Optional.ofNullable(sign);
    }

    Node setSign(TextToken sign) {
        switch (sign) {
            case PLUS -> this.sign = null;
            case MINUS -> this.sign = this.sign == null ? sign : null;
        }

        return this;
    }

    @Override
    public String toString() {
        return type.toString();
    }
}
