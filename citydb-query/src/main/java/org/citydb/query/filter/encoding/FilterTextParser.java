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

import org.citydb.query.filter.common.Expression;

public class FilterTextParser {

    private FilterTextParser() {
    }

    public static FilterTextParser newInstance() {
        return new FilterTextParser();
    }

    public Expression parse(String text) throws FilterParseException {
        return new FilterTextBuilder().build(parseRaw(text));
    }

    public <T extends Expression> T parse(String text, Class<T> type) throws FilterParseException {
        Expression expression = parse(text);
        return type.isInstance(expression) ?
                type.cast(expression) :
                null;
    }

    public Node parseRaw(String text) throws FilterParseException {
        text = text != null ? text.replaceAll("''", "\\\\'") : "";
        Tokenizer tokenizer = Tokenizer.of(text);

        Node node = readBooleanExpression(tokenizer);
        if (tokenizer.lookAhead() == Token.EOF) {
            return node;
        } else {
            throw new FilterParseException("Failed to parse filter text at '" + tokenizer.nextToken() + "'.");
        }
    }

    private Node readBooleanExpression(Tokenizer tokenizer) throws FilterParseException {
        Node left = readBooleanOperand(tokenizer);
        while (TextToken.BINARY_BOOLEAN_OPERATORS.contains(tokenizer.lookAhead().getType())) {
            Token operator = tokenizer.nextToken();
            Node right = readBooleanOperand(tokenizer);
            if (right != Node.EMPTY) {
                left = chain(left, operator, right);
            } else {
                throw new FilterParseException("Failed to parse boolean expression '" +
                        tokenizer.substring(right.getToken(), true) + "'.");
            }
        }

        return left;
    }

    private Node readBooleanOperand(Tokenizer tokenizer) throws FilterParseException {
        if (tokenizer.lookAhead().getType() == TextToken.L_PAREN) {
            tokenizer.saveState();
            tokenizer.nextToken();
            Node operand = readBooleanExpression(tokenizer);
            if (operand != Node.EMPTY
                    && tokenizer.nextToken().getType() == TextToken.R_PAREN
                    && !TextToken.BINARY_COMPARISON_OPERATORS.contains(tokenizer.lookAhead().getType())
                    && !TextToken.ARITHMETIC_OPERATORS.contains(tokenizer.lookAhead().getType())) {
                tokenizer.removeState();
                return operand.setEnclosed();
            } else {
                tokenizer.restoreState();
            }
        } else if (tokenizer.lookAhead().getType() == TextToken.NOT) {
            Token not = tokenizer.nextToken();
            Node operand = readBooleanOperand(tokenizer);
            if (operand != Node.EMPTY) {
                return Node.of(not).addChild(operand);
            } else {
                throw new FilterParseException("Failed to parse NOT predicate '" +
                        tokenizer.substring(not, true) + "'.");
            }
        }

        return readPredicate(tokenizer);
    }

    private Node readPredicate(Tokenizer tokenizer) throws FilterParseException {
        if (TextToken.BINARY_SPATIAL_OPERATORS.contains(tokenizer.lookAhead().getType())) {
            return readBinaryExpressionPredicate(tokenizer.nextToken(), this::readSpatialExpression, tokenizer);
        } else if (TextToken.SPATIAL_DISTANCE_OPERATORS.contains(tokenizer.lookAhead().getType())) {
            return readSpatialDistancePredicate(tokenizer.nextToken(), tokenizer);
        } else if (TextToken.TEMPORAL_OPERATORS.contains(tokenizer.lookAhead().getType())) {
            return readBinaryExpressionPredicate(tokenizer.nextToken(), this::readTemporalExpression, tokenizer);
        } else if (TextToken.ARRAY_OPERATORS.contains(tokenizer.lookAhead().getType())) {
            return readBinaryExpressionPredicate(tokenizer.nextToken(), this::readArrayExpression, tokenizer);
        } else {
            return readComparisonPredicate(tokenizer);
        }
    }

    private Node readStepPredicate(Node identifier, Tokenizer tokenizer) throws FilterParseException {
        if (tokenizer.lookAhead().getType() == TextToken.L_BRACKET) {
            tokenizer.nextToken();
            Node predicate = readBooleanExpression(tokenizer);
            require(tokenizer.nextToken(), TextToken.R_BRACKET, "Failed to parse property step predicate.");
            Node propertyRef = Node.of(NodeType.PROPERTY_REF, identifier.getToken()).addChild(predicate);

            if (tokenizer.lookAhead().getType() == TextToken.IDENTIFIER) {
                Node terminal = readTerminal(tokenizer);
                if (terminal.getToken().getValue().startsWith(".")) {
                    terminal.getToken().setValue(terminal.getToken().getValue().substring(1));
                }

                propertyRef.addChild(terminal);
            }

            return propertyRef;
        }

        return Node.EMPTY;
    }

    private Node readBinaryExpressionPredicate(Token function, ExpressionReader<Tokenizer, Node> reader, Tokenizer tokenizer) throws FilterParseException {
        require(tokenizer.nextToken(), TextToken.L_PAREN, "Failed to parse binary predicate.");
        Node left = reader.readExpression(tokenizer);
        require(tokenizer.nextToken(), TextToken.COMMA, "Failed to parse binary predicate.");
        Node right = reader.readExpression(tokenizer);
        require(tokenizer.nextToken(), TextToken.R_PAREN, "Failed to parse binary predicate.");

        if (left != Node.EMPTY
                && right != Node.EMPTY) {
            return Node.of(function).addChild(left).addChild(right);
        } else {
            throw new FilterParseException("Failed to parse binary predicate '" +
                    tokenizer.substring(function) + "'.");
        }
    }

    private Node readSpatialDistancePredicate(Token operator, Tokenizer tokenizer) throws FilterParseException {
        require(tokenizer.nextToken(), TextToken.L_PAREN, "Failed to parse spatial distance predicate.");
        Node left = readSpatialExpression(tokenizer);
        require(tokenizer.nextToken(), TextToken.COMMA, "Failed to parse spatial distance predicate.");
        Node right = readSpatialExpression(tokenizer);
        require(tokenizer.nextToken(), TextToken.COMMA, "Failed to parse spatial distance predicate.");
        Node distance = readNumericLiteral(tokenizer);

        Node unit = Node.EMPTY;
        if (tokenizer.lookAhead().getType() == TextToken.COMMA) {
            tokenizer.nextToken();
            unit = readLiteral(tokenizer);
        }

        require(tokenizer.nextToken(), TextToken.R_PAREN, "Failed to parse spatial distance predicate.");

        if (left != Node.EMPTY
                && right != Node.EMPTY
                && distance != Node.EMPTY) {
            return Node.of(operator).addChild(left).addChild(right).addChild(distance).addChild(unit);
        } else {
            throw new FilterParseException("Failed to parse spatial distance predicate '" +
                    tokenizer.substring(operator) + "'.");
        }
    }

    private Node readSpatialExpression(Tokenizer tokenizer) throws FilterParseException {
        Node geometry = readGeometryLiteral(tokenizer);
        return geometry != Node.EMPTY ?
                geometry :
                readTerminal(tokenizer);
    }

    private Node readTemporalExpression(Tokenizer tokenizer) throws FilterParseException {
        Node timeInterval = readTimeIntervalLiteral(tokenizer);
        return timeInterval != Node.EMPTY ?
                timeInterval :
                readTerminal(tokenizer);
    }

    private Node readArrayExpression(Tokenizer tokenizer) throws FilterParseException {
        Node array = readArray(tokenizer);
        return array != Node.EMPTY ?
                array :
                readTerminal(tokenizer);
    }

    private Node readComparisonPredicate(Tokenizer tokenizer) throws FilterParseException {
        Node left = readComparisonOperand(tokenizer);
        if (left != Node.EMPTY) {
            Token operator = tokenizer.nextToken();
            if (TextToken.BINARY_COMPARISON_OPERATORS.contains(operator.getType())) {
                return readBinaryComparisonPredicate(left, operator, tokenizer);
            } else if (operator.getType() == TextToken.LIKE
                    || operator.getType() == TextToken.NOT_LIKE) {
                return readLikePredicate(left, operator, tokenizer);
            } else if (operator.getType() == TextToken.BETWEEN
                    || operator.getType() == TextToken.NOT_BETWEEN) {
                return readBetweenPredicate(left, operator, tokenizer);
            } else if (operator.getType() == TextToken.IN
                    || operator.getType() == TextToken.NOT_IN) {
                return readInPredicate(left, operator, tokenizer);
            } else if (operator.getType() == TextToken.IS_NULL
                    || operator.getType() == TextToken.IS_NOT_NULL) {
                return Node.of(operator).addChild(left);
            } else {
                tokenizer.rewind();
                return left;
            }
        } else {
            return Node.EMPTY;
        }
    }

    private Node readBinaryComparisonPredicate(Node left, Token operator, Tokenizer tokenizer) throws FilterParseException {
        Node right = readComparisonOperand(tokenizer);
        if (right != Node.EMPTY) {
            return Node.of(operator).addChild(left).addChild(right);
        } else {
            throw new FilterParseException("Failed to parse comparison expression '" +
                    tokenizer.substring(right.getToken(), true) + "'.");
        }
    }

    private Node readLikePredicate(Node left, Token operator, Tokenizer tokenizer) throws FilterParseException {
        Node pattern = readTerminal(tokenizer);
        if (pattern != Node.EMPTY) {
            return Node.of(operator).addChild(left).addChild(pattern);
        } else {
            throw new FilterParseException("Failed to parse LIKE predicate '" +
                    tokenizer.substring(left.getToken(), true) + "'.");
        }
    }

    private Node readBetweenPredicate(Node left, Token operator, Tokenizer tokenizer) throws FilterParseException {
        Node lowerBound = readArithmeticExpression(tokenizer);
        require(tokenizer.nextToken(), TextToken.AND, "Failed to parse BETWEEN predicate.");
        Node upperBound = readArithmeticExpression(tokenizer);

        if (lowerBound != Node.EMPTY
                && upperBound != Node.EMPTY) {
            return Node.of(operator).addChild(left).addChild(lowerBound).addChild(upperBound);
        } else {
            throw new FilterParseException("Failed to parse BETWEEN predicate '" +
                    tokenizer.substring(left.getToken(), true) + "'.");
        }
    }

    private Node readInPredicate(Node left, Token operator, Tokenizer tokenizer) throws FilterParseException {
        require(tokenizer.nextToken(), TextToken.L_PAREN, "Failed to parse IN predicate.");
        Node in = Node.of(operator).addChild(left);
        do {
            in.addChild(readScalarExpression(tokenizer));
        } while (tokenizer.nextToken().getType() == TextToken.COMMA);

        require(tokenizer.currentToken(), TextToken.R_PAREN, "Failed to parse IN predicate.");
        return in;
    }

    private Node readComparisonOperand(Tokenizer tokenizer) throws FilterParseException {
        if (tokenizer.lookAhead().getType() == TextToken.L_PAREN) {
            tokenizer.saveState();
            tokenizer.nextToken();
            Node operand = readComparisonPredicate(tokenizer);
            if (operand != Node.EMPTY
                    && tokenizer.nextToken().getType() == TextToken.R_PAREN
                    && !TextToken.ARITHMETIC_OPERATORS.contains(tokenizer.lookAhead().getType())) {
                tokenizer.removeState();
                return operand;
            } else {
                tokenizer.restoreState();
            }
        }

        return readScalarExpression(tokenizer);
    }

    private Node readScalarExpression(Tokenizer tokenizer) throws FilterParseException {
        Node scalar = readArithmeticExpression(tokenizer);
        return scalar != Node.EMPTY ?
                scalar :
                readTerminal(tokenizer);
    }

    private Node readArithmeticExpression(Tokenizer tokenizer) throws FilterParseException {
        Node left = readArithmeticOperand(tokenizer);
        while (TextToken.ARITHMETIC_OPERATORS.contains(tokenizer.lookAhead().getType())) {
            Token operator = tokenizer.nextToken();
            Node right = readArithmeticOperand(tokenizer);
            if (right != Node.EMPTY) {
                left = chain(left, operator, right);
            } else {
                throw new FilterParseException("Failed to parse arithmetic expression '" +
                        tokenizer.substring(right.getToken(), true) + "'.");
            }
        }

        return left;
    }

    private Node readArithmeticOperand(Tokenizer tokenizer) throws FilterParseException {
        if (tokenizer.lookAhead().getType() == TextToken.L_PAREN) {
            tokenizer.nextToken();
            Node operand = readArithmeticExpression(tokenizer);
            require(tokenizer.nextToken(), TextToken.R_PAREN, "Failed to parse arithmetic expression.");
            return operand.setEnclosed();
        } else if (tokenizer.lookAhead().getType() == TextToken.PLUS
                || tokenizer.lookAhead().getType() == TextToken.MINUS) {
            Token sign = tokenizer.nextToken();
            Node operand = readArithmeticOperand(tokenizer);
            if (operand != Node.EMPTY) {
                return sign.getType() == TextToken.MINUS ?
                        operand.setSign(sign.getType()) :
                        operand;
            } else {
                throw new FilterParseException("Failed to parse signed arithmetic operand '" +
                        tokenizer.substring(sign, true) + "'.");
            }
        } else {
            return readTerminal(tokenizer);
        }
    }

    private Node readFunction(Node identifier, Tokenizer tokenizer) throws FilterParseException {
        if (tokenizer.lookAhead().getType() == TextToken.L_PAREN) {
            tokenizer.nextToken();
            Node function = Node.of(NodeType.FUNCTION, identifier.getToken());
            do {
                function.addChild(readArgument(tokenizer));
            } while (tokenizer.nextToken().getType() == TextToken.COMMA);

            require(tokenizer.currentToken(), TextToken.R_PAREN, "Failed to parse '" + identifier.getToken() +
                    "' function.");
            return function;
        }

        return Node.EMPTY;
    }

    private Node readArgument(Tokenizer tokenizer) throws FilterParseException {
        Node argument = readTimeIntervalLiteral(tokenizer);
        if (argument == Node.EMPTY) {
            argument = readGeometryLiteral(tokenizer);
        }

        if (argument == Node.EMPTY) {
            try {
                tokenizer.saveState();
                argument = readArray(tokenizer);
                tokenizer.removeState();
            } catch (FilterParseException e) {
                tokenizer.restoreState();
            }
        }

        if (argument == Node.EMPTY) {
            argument = readBooleanExpression(tokenizer);
        }

        return argument;
    }

    private Node readArray(Tokenizer tokenizer) throws FilterParseException {
        if (tokenizer.lookAhead().getType() == TextToken.L_PAREN) {
            Node array = Node.of(NodeType.ARRAY, tokenizer.nextToken());
            do {
                array.addChild(readArgument(tokenizer));
            } while (tokenizer.nextToken().getType() == TextToken.COMMA);

            require(tokenizer.currentToken(), TextToken.R_PAREN, "Failed to parse array.");
            return array;
        }

        return Node.EMPTY;
    }

    private Node readGeometryLiteral(Tokenizer tokenizer) throws FilterParseException {
        if (tokenizer.lookAhead().getType() == TextToken.BBOX) {
            return readBBoxLiteral(Node.of(tokenizer.nextToken()), tokenizer);
        } else if (TextToken.GEOMETRIES.contains(tokenizer.lookAhead().getType())) {
            return Node.of(tokenizer.nextToken());
        } else {
            return Node.EMPTY;
        }
    }

    private Node readBBoxLiteral(Node bbox, Tokenizer tokenizer) throws FilterParseException {
        require(tokenizer.nextToken(), TextToken.L_PAREN, "Failed to parse BBOX literal.");
        do {
            bbox.addChild(readNumericLiteral(tokenizer));
        } while (tokenizer.nextToken().getType() == TextToken.COMMA);

        require(tokenizer.currentToken(), TextToken.R_PAREN, "Failed to parse BBOX literal.");
        return bbox;
    }

    private Node readNumericLiteral(Tokenizer tokenizer) throws FilterParseException {
        Token sign = tokenizer.lookAhead().getType() == TextToken.PLUS
                || tokenizer.lookAhead().getType() == TextToken.MINUS ?
                tokenizer.nextToken() :
                Token.UNDEFINED;

        Node literal = readLiteral(tokenizer);
        if (literal != Node.EMPTY) {
            return sign.getType() == TextToken.MINUS ?
                    literal.setSign(sign.getType()) :
                    literal;
        } else {
            throw new FilterParseException("Failed to parse '" + tokenizer.currentToken() + "' as numeric literal.");
        }
    }

    private Node readTimeIntervalLiteral(Tokenizer tokenizer) throws FilterParseException {
        if (tokenizer.lookAhead().getType() == TextToken.INTERVAL) {
            Node interval = Node.of(tokenizer.nextToken());
            require(tokenizer.nextToken(), TextToken.L_PAREN, "Failed to parse time interval literal.");
            Node start = readTerminal(tokenizer);
            require(tokenizer.nextToken(), TextToken.COMMA, "Failed to parse time interval literal.");
            Node end = readTerminal(tokenizer);
            require(tokenizer.nextToken(), TextToken.R_PAREN, "Failed to parse time interval literal.");

            if (start != Node.EMPTY
                    && end != Node.EMPTY) {
                return interval.addChild(start).addChild(end);
            } else {
                throw new FilterParseException("Failed to parse time interval literal '" +
                        tokenizer.substring(interval.getToken()) + "'.");
            }
        } else {
            return Node.EMPTY;
        }
    }

    private Node readTerminal(Tokenizer tokenizer) throws FilterParseException {
        Node literal = readLiteral(tokenizer);
        if (literal != Node.EMPTY) {
            return switch (tokenizer.lookAhead().getType()) {
                case L_PAREN -> readFunction(literal, tokenizer);
                case L_BRACKET -> readStepPredicate(literal, tokenizer);
                default -> literal;
            };
        } else {
            return Node.EMPTY;
        }
    }

    private Node readLiteral(Tokenizer tokenizer) {
        Node node = Node.of(tokenizer.nextToken());
        if (NodeType.LITERALS.contains(node.getType())) {
            return node;
        } else {
            tokenizer.rewind();
            return Node.EMPTY;
        }
    }

    private Node chain(Node left, Token operator, Node right) {
        if (!left.isEnclosed()
                && (TextToken.ARITHMETIC_OPERATORS.contains(left.getToken().getType())
                || TextToken.BINARY_COMPARISON_OPERATORS.contains(left.getToken().getType())
                || TextToken.BINARY_BOOLEAN_OPERATORS.contains(left.getToken().getType()))
                && operator.getPrecedence().isHigher(left.getToken().getPrecedence())) {
            left.getChildren().set(1, Node.of(operator)
                    .addChild(left.getChildren().get(1))
                    .addChild(right));
            return left;
        } else {
            return Node.of(operator).addChild(left).addChild(right);
        }
    }

    private void require(Token token, TextToken type, String message) throws FilterParseException {
        if (token.getType() != type) {
            throw new FilterParseException(message,
                    new FilterParseException("Expected '" + type + "' but found '" + token + "'."));
        }
    }

    @FunctionalInterface
    private interface ExpressionReader<Tokenizer, Node> {
        Node readExpression(Tokenizer tokenizer) throws FilterParseException;
    }
}
