/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter.literal;

import org.citydb.query.filter.common.CharacterExpression;
import org.citydb.query.filter.common.FilterVisitor;
import org.citydb.query.filter.common.PatternExpression;
import org.citydb.query.filter.operation.Like;
import org.citydb.query.filter.operation.Operators;

public class StringLiteral extends Literal<String> implements PatternExpression, CharacterExpression {

    private StringLiteral(String value) {
        super(value);
    }

    public static StringLiteral of(String value) {
        return new StringLiteral(value);
    }

    public static StringLiteral of(Object value) {
        return new StringLiteral(String.valueOf(value));
    }

    public Like like(PatternExpression pattern) {
        return Operators.like(this, pattern);
    }

    public Like notLike(PatternExpression pattern) {
        return Operators.notLike(this, pattern);
    }

    @Override
    public void accept(FilterVisitor visitor) {
        visitor.visit(this);
    }
}
