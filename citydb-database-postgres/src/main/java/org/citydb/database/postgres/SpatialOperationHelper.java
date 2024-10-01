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

import org.citydb.sqlbuilder.function.Function;
import org.citydb.sqlbuilder.literal.IntegerLiteral;
import org.citydb.sqlbuilder.literal.ScalarExpression;
import org.citydb.sqlbuilder.literal.StringLiteral;
import org.citydb.sqlbuilder.operation.BinaryComparisonOperation;
import org.citydb.sqlbuilder.operation.BooleanExpression;
import org.citydb.sqlbuilder.operation.Operators;

public class SpatialOperationHelper implements org.citydb.database.util.SpatialOperationHelper {
    private final StringLiteral TRUE = StringLiteral.of("TRUE");

    @Override
    public Function extent(ScalarExpression operand) {
        return Function.of("st_3dextent", operand);
    }

    @Override
    public Function transform(ScalarExpression operand, int srid) {
        return Function.of("st_transform", operand, IntegerLiteral.of(srid));
    }

    @Override
    public BooleanExpression bbox(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return BinaryComparisonOperation.of(leftOperand, "&&", rightOperand);
    }

    @Override
    public BooleanExpression contains(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return Operators.eq(Function.of("st_contains", leftOperand, rightOperand), TRUE);
    }

    @Override
    public BooleanExpression crosses(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return Operators.eq(Function.of("st_crosses", leftOperand, rightOperand), TRUE);
    }

    @Override
    public BooleanExpression disjoint(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return Operators.eq(Function.of("st_disjoint", leftOperand, rightOperand), TRUE);
    }

    @Override
    public BooleanExpression equals(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return Operators.eq(Function.of("st_equals", leftOperand, rightOperand), TRUE);
    }

    @Override
    public BooleanExpression intersects(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return Operators.eq(Function.of("st_intersects", leftOperand, rightOperand), TRUE);
    }

    @Override
    public BooleanExpression overlaps(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return Operators.eq(Function.of("st_overlaps", leftOperand, rightOperand), TRUE);
    }

    @Override
    public BooleanExpression touches(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return Operators.eq(Function.of("st_touches", leftOperand, rightOperand), TRUE);
    }

    @Override
    public BooleanExpression within(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return Operators.eq(Function.of("st_within", leftOperand, rightOperand), TRUE);
    }

    @Override
    public BooleanExpression dWithin(ScalarExpression leftOperand, ScalarExpression rightOperand, ScalarExpression distance) {
        return Operators.eq(Function.of("st_dwithin", leftOperand, rightOperand, distance), TRUE);
    }

    @Override
    public BooleanExpression beyond(ScalarExpression leftOperand, ScalarExpression rightOperand, ScalarExpression distance) {
        return Operators.ne(Function.of("st_dwithin", leftOperand, rightOperand, distance), TRUE);
    }
}
