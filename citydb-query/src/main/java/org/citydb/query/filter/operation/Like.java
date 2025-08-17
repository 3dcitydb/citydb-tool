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

package org.citydb.query.filter.operation;

import org.citydb.query.filter.common.CharacterExpression;
import org.citydb.query.filter.common.ComparisonPredicate;
import org.citydb.query.filter.common.FilterVisitor;
import org.citydb.query.filter.common.PatternExpression;

import java.util.Objects;

public class Like implements ComparisonPredicate {
    public static final String WILDCARD = "%";
    public static final String SINGLE_CHAR = "_";
    public static final String ESCAPE_CHAR = "\\";
    private final CharacterExpression operand;
    private final PatternExpression pattern;
    private ComparisonOperator operator;

    private Like(CharacterExpression operand, PatternExpression pattern, boolean negate) {
        this.operand = Objects.requireNonNull(operand, "The operand must not be null.");
        this.pattern = Objects.requireNonNull(pattern, "The pattern must not be null.");
        this.operator = negate ? ComparisonOperator.LIKE.negate() : ComparisonOperator.LIKE;
    }

    public static Like of(CharacterExpression operand, PatternExpression pattern, boolean negate) {
        return new Like(operand, pattern, negate);
    }

    public static Like of(CharacterExpression operand, PatternExpression pattern) {
        return new Like(operand, pattern, false);
    }

    public CharacterExpression getOperand() {
        return operand;
    }

    public PatternExpression getPattern() {
        return pattern;
    }

    public ComparisonOperator getOperator() {
        return operator;
    }

    @Override
    public Like negate() {
        operator = operator.negate();
        return this;
    }

    @Override
    public void accept(FilterVisitor visitor) {
        visitor.visit(this);
    }
}
