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

package org.citydb.database.postgres;

import org.citydb.database.adapter.SchemaAdapter;
import org.citydb.sqlbuilder.common.Expression;
import org.citydb.sqlbuilder.function.Function;

public class OperationHelper extends org.citydb.database.schema.OperationHelper {

    OperationHelper(SchemaAdapter schemaAdapter) {
        super(schemaAdapter);
    }

    @Override
    public Expression intDivision(Expression leftOperand, Expression rightOperand) {
        return Function.of("div", leftOperand, rightOperand);
    }

    @Override
    public Expression lower(Expression expression) {
        return Function.of("lower", expression);
    }

    @Override
    public Expression toDate(Expression expression) {
        return Function.of("date", expression);
    }

    @Override
    public Expression power(Expression leftOperand, Expression rightOperand) {
        return Function.of("power", leftOperand, rightOperand);
    }

    @Override
    public Expression unaccent(Expression expression) {
        return Function.of("unaccent", expression);
    }

    @Override
    public Expression upper(Expression expression) {
        return Function.of("upper", expression);
    }
}
