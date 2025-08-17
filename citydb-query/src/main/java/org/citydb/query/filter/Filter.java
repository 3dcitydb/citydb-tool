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
