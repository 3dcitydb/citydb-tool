/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
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

import java.io.IOException;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.util.*;

public class Tokenizer {
    private final List<Token> tokens = new ArrayList<>();
    private final Deque<Integer> states = new ArrayDeque<>();
    private int index;

    private Tokenizer() {
    }

    static Tokenizer of(String text) throws FilterParseException {
        return new Tokenizer().initialize(Objects.requireNonNull(text, "The text must not be null."));
    }

    Token currentToken() {
        return getToken(index - 1);
    }

    Token nextToken() {
        return getToken(index++);
    }

    Token lookAhead() {
        return getToken(index);
    }

    void rewind() {
        index--;
    }

    void saveState() {
        states.push(index);
    }

    void restoreState() {
        if (!states.isEmpty()) {
            index = states.pop();
        }
    }

    void removeState() {
        if (!states.isEmpty()) {
            states.pop();
        }
    }

    String substring(Token beginToken) {
        return substring(beginToken, false);
    }

    String substring(Token beginToken, boolean includeNext) {
        return substring(indexOf(beginToken), includeNext ? index + 1 : index);
    }

    private String substring(int beginIndex, int endIndex) {
        beginIndex = Math.max(0, beginIndex);
        endIndex = Math.min(endIndex, tokens.size());

        List<String> tokens = new ArrayList<>();
        for (int i = beginIndex; i < endIndex; i++) {
            Token token = this.tokens.get(i);
            tokens.add(switch (token.getType()) {
                case SINGLE_QUOTE -> "'" + token + "'";
                case DOUBLE_QUOTE -> "\"" + token + "\"";
                default -> token.toString();
            });
        }

        return String.join(" ", tokens);
    }

    private Token getToken(int index) {
        return index >= 0 && index < tokens.size() ?
                tokens.get(index) :
                Token.EOF;
    }

    private int indexOf(Token token) {
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i) == token) {
                return i;
            }
        }

        return 0;
    }

    private Tokenizer initialize(String text) throws FilterParseException {
        StreamTokenizer tokenizer = createTokenizer(text);
        Token token;
        while ((token = nextToken(tokenizer)) != Token.EOF) {
            TextToken type = token.getType();
            if (type == TextToken.LESS_THAN) {
                token = rewriteLessThan(token, tokenizer);
            } else if (type == TextToken.GREATER_THAN) {
                token = rewriteGreaterThan(token, tokenizer);
            } else if (token.getType() == TextToken.IS) {
                token = rewriteIsNull(token, tokenizer);
            } else if (token.getType() == TextToken.NOT) {
                token = rewriteNot(token, tokenizer);
            } else if (token.getType() == TextToken.DATE
                    || token.getType() == TextToken.TIMESTAMP) {
                token = rewriteTimeInstant(token, tokenizer);
            } else if (TextToken.GEOMETRIES.contains(token.getType())
                    && token.getType() != TextToken.BBOX) {
                token = rewriteGeometry(token, tokenizer);
            } else if (token.getType() == TextToken.DOUBLE_QUOTE) {
                token = rewriteTypeCast(token, tokenizer);
            } else if (token.getType() == TextToken.IDENTIFIER) {
                failOnTypeCast(token);
            }

            tokens.add(token);
        }

        return this;
    }

    private Token rewriteLessThan(Token token, StreamTokenizer tokenizer) throws FilterParseException {
        if (lookAhead(tokenizer).getType() == TextToken.EQUAL_TO) {
            nextToken(tokenizer);
            return Token.of(TextToken.LESS_THAN_OR_EQUAL_TO);
        } else if (lookAhead(tokenizer).getType() == TextToken.GREATER_THAN) {
            nextToken(tokenizer);
            return Token.of(TextToken.NOT_EQUAL_TO);
        } else {
            return token;
        }
    }

    private Token rewriteGreaterThan(Token token, StreamTokenizer tokenizer) throws FilterParseException {
        if (lookAhead(tokenizer).getType() == TextToken.EQUAL_TO) {
            nextToken(tokenizer);
            return Token.of(TextToken.GREATER_THAN_OR_EQUAL_TO);
        } else {
            return token;
        }
    }

    private Token rewriteIsNull(Token token, StreamTokenizer tokenizer) throws FilterParseException {
        String parsed = TextToken.IS.value();
        Token next = nextToken(tokenizer);
        if (next.getType() == TextToken.NULL) {
            token = Token.of(TextToken.IS_NULL);
        } else if (next.getType() == TextToken.NOT) {
            parsed += " " + TextToken.NOT;
            next = nextToken(tokenizer);
            if (next.getType() == TextToken.NULL) {
                token = Token.of(TextToken.IS_NOT_NULL);
            }
        }

        if (token.getType() == TextToken.IS_NULL
                || token.getType() == TextToken.IS_NOT_NULL) {
            return token;
        } else {
            throw new FilterParseException("Unexpected token '" + next + "' following '" + parsed + "'.");
        }
    }

    private Token rewriteNot(Token token, StreamTokenizer tokenizer) throws FilterParseException {
        if (lookAhead(tokenizer).getType() == TextToken.LIKE) {
            nextToken(tokenizer);
            return Token.of(TextToken.NOT_LIKE);
        } else if (lookAhead(tokenizer).getType() == TextToken.BETWEEN) {
            nextToken(tokenizer);
            return Token.of(TextToken.NOT_BETWEEN);
        } else if (lookAhead(tokenizer).getType() == TextToken.IN) {
            nextToken(tokenizer);
            return Token.of(TextToken.NOT_IN);
        } else {
            return token;
        }
    }

    private Token rewriteTimeInstant(Token token, StreamTokenizer tokenizer) throws FilterParseException {
        Token lparen = nextToken(tokenizer);
        Token instant = nextToken(tokenizer);
        Token rparen = nextToken(tokenizer);

        if (lparen.getType() == TextToken.L_PAREN
                && TextToken.QUOTE_CHARS.contains(instant.getType())
                && rparen.getType() == TextToken.R_PAREN) {
            return Token.of(token.getType(), instant.getValue());
        } else {
            throw new FilterParseException("Failed to parse time instant literal '" + token + lparen +
                    instant + rparen + "'.");
        }
    }

    private Token rewriteGeometry(Token token, StreamTokenizer tokenizer) throws FilterParseException {
        StringBuilder wkt = new StringBuilder(token.getValue());
        if (lookAhead(tokenizer).getValue().equalsIgnoreCase("EMPTY")) {
            nextToken(tokenizer);
            return Token.of(token.getType(), wkt.append(" EMPTY").toString());
        } else {
            if (lookAhead(tokenizer).getValue().equalsIgnoreCase("Z")
                    || lookAhead(tokenizer).getValue().equalsIgnoreCase("M")
                    || lookAhead(tokenizer).getValue().equalsIgnoreCase("ZM")) {
                wkt.append(" ").append(nextToken(tokenizer)).append(" ");
            }

            if (lookAhead(tokenizer).getType() != TextToken.L_PAREN) {
                throw new FilterParseException("Expected '" + TextToken.L_PAREN + "' but found " +
                        "'" + lookAhead(tokenizer) + "' while parsing a " + token + " literal.");
            }
        }

        int balanced = 0;
        Token next;
        while ((next = nextToken(tokenizer)) != Token.EOF) {
            wkt.append(next.getValue());
            if (next.getType() == TextToken.IDENTIFIER) {
                wkt.append(" ");
            }

            if (next.getType() == TextToken.L_PAREN) {
                balanced++;
            } else if (next.getType() == TextToken.R_PAREN) {
                balanced--;
                if (balanced == 0) {
                    break;
                }
            }
        }

        return Token.of(token.getType(), wkt.toString());
    }

    private Token rewriteTypeCast(Token token, StreamTokenizer tokenizer) throws FilterParseException {
        Token next = lookAhead(tokenizer);
        if (next.getType() == TextToken.IDENTIFIER
                && next.getValue().startsWith("::")) {
            nextToken(tokenizer);
            return Token.of(token.getType(), token.getValue() + next.getValue());
        } else {
            return token;
        }
    }

    private void failOnTypeCast(Token token) throws FilterParseException {
        int index = token.getValue().indexOf("::");
        if (index != -1) {
            String value = token.getValue().substring(0, index);
            TextToken type = TextToken.of(value);
            if (index == 0
                    || type == TextToken.TRUE
                    || type == TextToken.FALSE
                    || isNumber(value)) {
                throw new FilterParseException("Invalid use of type cast '" +
                        token.getValue().substring(index) + "'.");
            }
        }
    }

    private boolean isNumber(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private Token nextToken(StreamTokenizer tokenizer) throws FilterParseException {
        try {
            int token = tokenizer.nextToken();
            if (token != StreamTokenizer.TT_EOF) {
                TextToken textToken = tokenizer.ttype == StreamTokenizer.TT_WORD ?
                        TextToken.of(tokenizer.sval, TextToken.IDENTIFIER) :
                        TextToken.of(String.valueOf((char) token), TextToken.IDENTIFIER);
                String value = tokenizer.sval != null ?
                        tokenizer.sval :
                        textToken.value();

                return Token.of(textToken, value);
            } else {
                return Token.EOF;
            }
        } catch (IOException e) {
            throw new FilterParseException("Failed to parse next token from input text.", e);
        }
    }

    private Token lookAhead(StreamTokenizer tokenizer) throws FilterParseException {
        Token token = nextToken(tokenizer);
        tokenizer.pushBack();
        return token;
    }

    private StreamTokenizer createTokenizer(String text) {
        StreamTokenizer tokenizer = new StreamTokenizer(new StringReader(text));
        tokenizer.resetSyntax();
        tokenizer.wordChars('\u0000', '\uFFFF');
        tokenizer.whitespaceChars(0, ' ');
        TextToken.QUOTE_CHARS.forEach(quote -> tokenizer.quoteChar(quote.value().charAt(0)));
        TextToken.SYNTAX_CHARS.forEach(syntax -> tokenizer.ordinaryChar(syntax.value().charAt(0)));
        return tokenizer;
    }
}
