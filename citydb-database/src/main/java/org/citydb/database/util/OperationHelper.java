/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.util;

import org.citydb.database.adapter.SchemaAdapter;
import org.citydb.sqlbuilder.common.Expression;
import org.citydb.sqlbuilder.literal.Literal;
import org.citydb.sqlbuilder.literal.Placeholder;
import org.citydb.sqlbuilder.literal.ScalarExpression;
import org.citydb.sqlbuilder.operation.BooleanExpression;
import org.citydb.sqlbuilder.operation.In;
import org.citydb.sqlbuilder.operation.Operators;

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

    public abstract BooleanExpression inArray(ScalarExpression operand, Placeholder values);

    public BooleanExpression in(ScalarExpression operand, Collection<?> values) {
        return in(operand, values, false);
    }

    public BooleanExpression in(ScalarExpression operand, Collection<?> values, boolean negate) {
        List<? extends Literal<?>> literals = values.stream().map(Literal::of).toList();
        if (literals.isEmpty()) {
            return Operators.isNull(operand, negate);
        } else if (literals.size() == 1) {
            return Operators.eq(operand, literals.iterator().next(), negate);
        }

        int size = schemaAdapter.getMaximumNumberOfItemsForInOperator();
        if (literals.size() > size) {
            List<In> operators = new ArrayList<>();
            while (literals.size() > size) {
                operators.add(In.of(operand, literals.subList(0, size), negate));
                literals = literals.subList(size, literals.size());
            }

            if (!literals.isEmpty()) {
                operators.add(In.of(operand, literals, negate));
            }

            return negate ? Operators.and(operators) : Operators.or(operators);
        } else {
            return In.of(operand, literals, negate);
        }
    }
}
