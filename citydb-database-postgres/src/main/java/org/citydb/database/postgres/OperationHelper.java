/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.postgres;

import org.citydb.database.adapter.SchemaAdapter;
import org.citydb.sqlbuilder.common.Expression;
import org.citydb.sqlbuilder.function.Function;
import org.citydb.sqlbuilder.literal.Placeholder;
import org.citydb.sqlbuilder.literal.ScalarExpression;
import org.citydb.sqlbuilder.operation.BooleanExpression;

public class OperationHelper extends org.citydb.database.util.OperationHelper {

    OperationHelper(SchemaAdapter schemaAdapter) {
        super(schemaAdapter);
    }

    @Override
    public Expression intDivision(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return Function.of("div", leftOperand, rightOperand);
    }

    @Override
    public Expression lower(ScalarExpression expression) {
        return Function.of("lower", expression);
    }

    @Override
    public Expression toDate(ScalarExpression expression) {
        return Function.of("date", expression);
    }

    @Override
    public Expression power(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return Function.of("power", leftOperand, rightOperand);
    }

    @Override
    public Expression unaccent(ScalarExpression expression) {
        return Function.of("unaccent", expression);
    }

    @Override
    public Expression upper(ScalarExpression expression) {
        return Function.of("upper", expression);
    }

    @Override
    public BooleanExpression inArray(ScalarExpression operand, Placeholder values) {
        return operand.eqAny(values);
    }
}
