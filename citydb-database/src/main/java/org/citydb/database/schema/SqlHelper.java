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
import org.citydb.sqlbuilder.literal.Placeholder;
import org.citydb.sqlbuilder.operation.BooleanExpression;
import org.citydb.sqlbuilder.operation.In;
import org.citydb.sqlbuilder.operation.Operators;
import org.citydb.sqlbuilder.schema.Column;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class SqlHelper {
    protected final SchemaAdapter schemaAdapter;

    protected SqlHelper(SchemaAdapter schemaAdapter) {
        this.schemaAdapter = schemaAdapter;
    }

    public abstract Expression getToDateFunction(Expression expression);
    public abstract Expression getUpperFunction(Expression expression);

    public BooleanExpression getInOperator(Column column, Collection<?> values) {
        return getInOperator(column, values, false);
    }

    public BooleanExpression getInOperator(Column column, Collection<?> values, boolean negate) {
        List<Placeholder> placeholders = values != null ?
                values.stream().map(Placeholder::of).toList() :
                null;
        if (placeholders == null || placeholders.isEmpty()) {
            return Operators.isNull(column, negate);
        } else if (placeholders.size() == 1) {
            return Operators.eq(column, placeholders.iterator().next(), negate);
        }

        int size = schemaAdapter.getMaximumNumberOfItemsForInOperator();
        if (placeholders.size() > size) {
            List<In> operators = new ArrayList<>();
            while (placeholders.size() > size) {
                operators.add(In.of(column, placeholders.subList(0, size), negate));
                placeholders = placeholders.subList(size, placeholders.size());
            }

            if (!placeholders.isEmpty()) {
                operators.add(In.of(column, placeholders, negate));
            }

            return negate ? Operators.and(operators) : Operators.or(operators);
        } else {
            return In.of(column, placeholders, negate);
        }
    }
}