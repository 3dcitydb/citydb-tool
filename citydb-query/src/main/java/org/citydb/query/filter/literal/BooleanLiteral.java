/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter.literal;

import org.citydb.query.filter.common.FilterVisitor;
import org.citydb.query.filter.common.ScalarExpression;
import org.citydb.query.filter.operation.BooleanExpression;

import java.util.Locale;

public class BooleanLiteral extends Literal<Boolean> implements BooleanExpression, ScalarExpression {
    public static final BooleanLiteral TRUE = new BooleanLiteral(true);
    public static final BooleanLiteral FALSE = new BooleanLiteral(false);

    private BooleanLiteral(Boolean value) {
        super(value);
    }

    public static BooleanLiteral of(boolean value) {
        return value ? TRUE : FALSE;
    }

    public static BooleanLiteral of(Object value) {
        if (value instanceof Boolean bool) {
            return BooleanLiteral.of(bool.booleanValue());
        } else if (value instanceof Number number) {
            return number.doubleValue() == 1 ? BooleanLiteral.TRUE : BooleanLiteral.FALSE;
        } else {
            return switch (String.valueOf(value).toLowerCase(Locale.ROOT)) {
                case "true", "yes", "y", "on", "1" -> BooleanLiteral.TRUE;
                default -> BooleanLiteral.FALSE;
            };
        }
    }

    @Override
    public void accept(FilterVisitor visitor) {
        visitor.visit(this);
    }
}
