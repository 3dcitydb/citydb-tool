/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter;

import org.citydb.query.filter.encoding.FilterJSONParser;
import org.citydb.query.filter.encoding.FilterParseException;
import org.citydb.query.filter.encoding.FilterTextParser;
import org.citydb.query.filter.operation.BooleanExpression;

import java.util.Objects;

public class Filter {
    private final BooleanExpression expression;

    private Filter(BooleanExpression expression) {
        this.expression = Objects.requireNonNull(expression, "The filter expression must not be null.");
    }

    public static Filter of(BooleanExpression expression) {
        return new Filter(expression);
    }

    public static Filter ofText(String text) throws FilterParseException {
        return ofParsed(FilterTextParser.newInstance().parse(text, BooleanExpression.class));
    }

    public static Filter ofJSON(Object json) throws FilterParseException {
        return ofParsed(FilterJSONParser.newInstance().parse(json, BooleanExpression.class));
    }

    private static Filter ofParsed(BooleanExpression expression) throws FilterParseException {
        if (expression != null) {
            return new Filter(expression);
        } else {
            throw new FilterParseException("Failed to parse input as boolean filter expression.");
        }
    }

    public BooleanExpression getExpression() {
        return expression;
    }
}
