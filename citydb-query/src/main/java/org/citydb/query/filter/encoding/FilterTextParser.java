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

    public Expression build(String text) throws FilterParseException {
        return new FilterTextBuilder().build(parse(text));
    }

    public <T extends Expression> T build(String text, Class<T> type) throws FilterParseException {
        Expression expression = new FilterTextBuilder().build(parse(text));
        return type.isInstance(expression) ?
                type.cast(expression) :
                null;
    }

    public Node parse(String text) throws FilterParseException {
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
                throw new FilterParseException("Failed to parse boolean expression '" + left.getToken() + " " +
                        operator + " " + tokenizer.nextToken() + "'.");
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
                throw new FilterParseException("Failed to parse NOT predicate '" + not + " " +
                        tokenizer.nextToken() + "'.");
            }
        }

        return readPredicate(tokenizer);
    }

    private Node readPredicate(Tokenizer tokenizer) throws FilterParseException {
        if (TextToken.SPATIAL_OPERATORS.contains(tokenizer.lookAhead().getType())) {
            return readBinaryExpressionPredicate(tokenizer.nextToken(), this::readSpatialExpression, tokenizer);
        } else if (TextToken.TEMPORAL_OPERATORS.contains(tokenizer.lookAhead().getType())) {
            return readBinaryExpressionPredicate(tokenizer.nextToken(), this::readTemporalExpression, tokenizer);
        } else if (TextToken.ARRAY_OPERATORS.contains(tokenizer.lookAhead().getType())) {
            return readBinaryExpressionPredicate(tokenizer.nextToken(), this::readArrayExpression, tokenizer);
        } else {
            return readComparisonPredicate(tokenizer);
        }
    }

    private Node readPropertyPredicate(Node identifier, Tokenizer tokenizer) throws FilterParseException {
        if (tokenizer.lookAhead().getType() == TextToken.L_BRACKET) {
            tokenizer.nextToken();
            Node predicate = readBooleanExpression(tokenizer);

            if (tokenizer.nextToken().getType() == TextToken.R_BRACKET) {
                Node property = Node.of(NodeType.PROPERTY, identifier.getToken()).addChild(predicate);
                if (tokenizer.lookAhead().getType() == TextToken.IDENTIFIER) {
                    Node terminal = readTerminal(tokenizer);
                    if (terminal.getToken().getValue().startsWith(".")) {
                        terminal.getToken().setValue(terminal.getToken().getValue().substring(1));
                    }

                    property.addChild(terminal);
                }

                return property;
            } else {
                throw new FilterParseException("Expected '" + TextToken.R_BRACKET + "' but found '" +
                        tokenizer.currentToken() + "' while parsing a property predicate.");
            }
        }

        return Node.EMPTY;
    }

    private Node readBinaryExpressionPredicate(Token function, ExpressionReader<Tokenizer, Node> reader, Tokenizer tokenizer) throws FilterParseException {
        Token lparen = tokenizer.nextToken();
        Node left = reader.readExpression(tokenizer);
        Token comma = tokenizer.nextToken();
        Node right = reader.readExpression(tokenizer);

        if (lparen.getType() == TextToken.L_PAREN
                && left != Node.EMPTY
                && comma.getType() == TextToken.COMMA
                && right != Node.EMPTY
                && tokenizer.nextToken().getType() == TextToken.R_PAREN) {
            return Node.of(function).addChild(left).addChild(right);
        } else {
            throw new FilterParseException("Failed to parse binary expression predicate '" + function +
                    lparen + left.getToken() + comma + right.getToken() + tokenizer.currentToken() + "'.");
        }
    }

    private Node readSpatialExpression(Tokenizer tokenizer) throws FilterParseException {
        Node geometry = readGeometryLiteral(tokenizer);
        return geometry != Node.EMPTY ?
                geometry :
                readTerminal(tokenizer);
    }

    private Node readTemporalExpression(Tokenizer tokenizer) throws FilterParseException {
        Node geometry = readTimeIntervalLiteral(tokenizer);
        return geometry != Node.EMPTY ?
                geometry :
                readTerminal(tokenizer);
    }

    private Node readArrayExpression(Tokenizer tokenizer) throws FilterParseException {
        Node geometry = readArray(tokenizer);
        return geometry != Node.EMPTY ?
                geometry :
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
            throw new FilterParseException("Failed to parse binary comparison predicate '" + left.getToken() + " " +
                    operator + " " + tokenizer.nextToken() + "'.");
        }
    }

    private Node readLikePredicate(Node left, Token operator, Tokenizer tokenizer) throws FilterParseException {
        Node pattern = readTerminal(tokenizer);
        if (pattern != Node.EMPTY) {
            return Node.of(operator).addChild(left).addChild(pattern);
        } else {
            throw new FilterParseException("Failed to parse LIKE predicate '" + left.getToken() + " " +
                    operator + " " + tokenizer.nextToken() + "'.");
        }
    }

    private Node readBetweenPredicate(Node left, Token operator, Tokenizer tokenizer) throws FilterParseException {
        Node lowerBound = readArithmeticExpression(tokenizer);
        Token and = tokenizer.nextToken();
        Node upperBound = readArithmeticExpression(tokenizer);

        if (lowerBound != Node.EMPTY
                && and.getType() == TextToken.AND
                && upperBound != Node.EMPTY) {
            return Node.of(operator).addChild(left).addChild(lowerBound).addChild(upperBound);
        } else {
            throw new FilterParseException("Failed to parse BETWEEN predicate '" + left.getToken() + " "
                    + operator + " " + lowerBound.getToken() + " " + and + " " + tokenizer.nextToken() + "'.");
        }
    }

    private Node readInPredicate(Node left, Token operator, Tokenizer tokenizer) throws FilterParseException {
        Token lparen = tokenizer.nextToken();
        Node in = Node.of(operator).addChild(left);
        do {
            in.addChild(readScalarExpression(tokenizer));
        } while (tokenizer.nextToken().getType() == TextToken.COMMA);

        if (lparen.getType() == TextToken.L_PAREN
                && tokenizer.currentToken().getType() == TextToken.R_PAREN) {
            return in;
        } else {
            throw new FilterParseException("Failed to parse IN predicate '" + left.getToken() + " "
                    + operator + lparen + tokenizer.currentToken() + "'.");
        }
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
                throw new FilterParseException("Failed to parse arithmetic expression '" + left.getToken() + " " +
                        operator + " " + tokenizer.nextToken() + "'.");
            }
        }

        return left;
    }

    private Node readArithmeticOperand(Tokenizer tokenizer) throws FilterParseException {
        if (tokenizer.lookAhead().getType() == TextToken.L_PAREN) {
            tokenizer.nextToken();
            Node operand = readArithmeticExpression(tokenizer);
            if (tokenizer.nextToken().getType() == TextToken.R_PAREN) {
                return operand.setEnclosed();
            } else {
                throw new FilterParseException("Expected '" + TextToken.R_PAREN + "' but found '" +
                        tokenizer.currentToken() + "' while parsing an arithmetic expression.");
            }
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
                        sign + tokenizer.nextToken() + "'.");
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

            if (tokenizer.currentToken().getType() == TextToken.R_PAREN) {
                return function;
            } else {
                throw new FilterParseException("Expected '" + TextToken.R_PAREN + "' but found '" +
                        tokenizer.currentToken() + "' while parsing the '" + identifier.getToken() + "' function.");
            }
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

            if (tokenizer.currentToken().getType() == TextToken.R_PAREN) {
                return array;
            } else {
                throw new FilterParseException("Expected '" + TextToken.R_PAREN + "' but found '" +
                        tokenizer.currentToken() + "' while parsing an array.");
            }
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
        Token lparen = tokenizer.nextToken();
        do {
            bbox.addChild(readNumericLiteral(tokenizer));
        } while (tokenizer.nextToken().getType() == TextToken.COMMA);

        if (lparen.getType() == TextToken.L_PAREN
                && tokenizer.currentToken().getType() == TextToken.R_PAREN) {
            return bbox;
        } else {
            throw new FilterParseException("Failed to parse BBOX literal '" + bbox.getToken() + " "
                    + lparen + tokenizer.currentToken() + "'.");
        }
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
            Token lparen = tokenizer.nextToken();
            Node start = readTerminal(tokenizer);
            Token comma = tokenizer.nextToken();
            Node end = readTerminal(tokenizer);

            if (lparen.getType() == TextToken.L_PAREN
                    && start != Node.EMPTY
                    && comma.getType() == TextToken.COMMA
                    && end != Node.EMPTY
                    && tokenizer.nextToken().getType() == TextToken.R_PAREN) {
                return interval.addChild(start).addChild(end);
            } else {
                throw new FilterParseException("Failed to parse time interval literal '" + interval.getToken() +
                        lparen + start.getToken() + comma + end.getToken() + tokenizer.currentToken() + "'.");
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
                case L_BRACKET -> readPropertyPredicate(literal, tokenizer);
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

    @FunctionalInterface
    private interface ExpressionReader<Tokenizer, Node> {
        Node readExpression(Tokenizer tokenizer) throws FilterParseException;
    }
}
