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
