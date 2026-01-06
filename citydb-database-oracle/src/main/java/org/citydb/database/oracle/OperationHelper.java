/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright © 2025, Oracle and/or its affiliates.
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

package org.citydb.database.oracle;

import org.citydb.database.adapter.SchemaAdapter;
import org.citydb.sqlbuilder.common.Expression;
import org.citydb.sqlbuilder.function.Function;
import org.citydb.sqlbuilder.operation.ArithmeticOperation;
import org.citydb.sqlbuilder.literal.ScalarExpression;

public class OperationHelper extends org.citydb.database.util.OperationHelper {

    OperationHelper(SchemaAdapter schemaAdapter) {
        super(schemaAdapter);
    }

    @Override
    public Expression intDivision(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return Function.of("trunc", ArithmeticOperation.of(leftOperand, "/", rightOperand));
    }

    @Override
    public Expression lower(ScalarExpression expression) {
        return Function.of("lower", expression);
    }

    @Override
    public Expression toDate(ScalarExpression expression) {
        return Function.of("to_date", expression);
    }

    @Override
    public Expression power(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return Function.of("power", leftOperand, rightOperand);
    }

    @Override
    public Expression unaccent(ScalarExpression expression) {
        // Oracle Database does not have a direct equivalent to PostgreSQL's unaccent function.
        // You may need to create a custom function or use a different approach.
        throw new UnsupportedOperationException("unaccent is not supported in Oracle Database.");
    }

    @Override
    public Expression upper(ScalarExpression expression) {
        return Function.of("upper", expression);
    }
}