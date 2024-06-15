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

package org.citydb.database.schema;

import org.citydb.database.adapter.SchemaAdapter;
import org.citydb.sqlbuilder.common.Expression;
import org.citydb.sqlbuilder.literal.Literal;
import org.citydb.sqlbuilder.literal.ScalarExpression;
import org.citydb.sqlbuilder.operation.BooleanExpression;
import org.citydb.sqlbuilder.operation.In;
import org.citydb.sqlbuilder.operation.Operators;
import org.citydb.sqlbuilder.schema.Column;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class OperationHelper {
    protected final SchemaAdapter schemaAdapter;

    protected OperationHelper(SchemaAdapter schemaAdapter) {
        this.schemaAdapter = schemaAdapter;
    }

    public abstract Expression intDivision(ScalarExpression leftOperand, ScalarExpression rightOperand);

    public abstract Expression lower(ScalarExpression expression);

    public abstract Expression toDate(ScalarExpression expression);

    public abstract Expression power(ScalarExpression leftOperand, ScalarExpression rightOperand);

    public abstract Expression unaccent(ScalarExpression expression);

    public abstract Expression upper(ScalarExpression expression);

    public BooleanExpression in(Column column, Collection<?> values) {
        return in(column, values, false);
    }

    public BooleanExpression in(Column column, Collection<?> values, boolean negate) {
        List<ScalarExpression> expressions = Literal.ofScalarList(values);
        if (expressions == null || expressions.isEmpty()) {
            return Operators.isNull(column, negate);
        } else if (expressions.size() == 1) {
            return Operators.eq(column, expressions.iterator().next(), negate);
        }

        int size = schemaAdapter.getMaximumNumberOfItemsForInOperator();
        if (expressions.size() > size) {
            List<In> operators = new ArrayList<>();
            while (expressions.size() > size) {
                operators.add(In.of(column, expressions.subList(0, size), negate));
                expressions = expressions.subList(size, expressions.size());
            }

            if (!expressions.isEmpty()) {
                operators.add(In.of(column, expressions, negate));
            }

            return negate ? Operators.and(operators) : Operators.or(operators);
        } else {
            return In.of(column, expressions, negate);
        }
    }
}
