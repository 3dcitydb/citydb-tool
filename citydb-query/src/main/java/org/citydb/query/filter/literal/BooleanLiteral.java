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
