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

package org.citydb.database.postgres;

import org.citydb.sqlbuilder.function.Cast;
import org.citydb.sqlbuilder.function.Function;
import org.citydb.sqlbuilder.literal.IntegerLiteral;
import org.citydb.sqlbuilder.literal.Placeholder;
import org.citydb.sqlbuilder.literal.ScalarExpression;
import org.citydb.sqlbuilder.literal.StringLiteral;
import org.citydb.sqlbuilder.operation.BinaryComparisonOperation;
import org.citydb.sqlbuilder.operation.BooleanExpression;
import org.citydb.sqlbuilder.operation.Operators;

public class SpatialOperationHelper implements org.citydb.database.util.SpatialOperationHelper {
    private final StringLiteral TRUE = StringLiteral.of("TRUE");

    SpatialOperationHelper() {
    }

    @Override
    public Function extent(ScalarExpression operand) {
        return Function.of("st_3dextent", cast(operand));
    }

    @Override
    public Function transform(ScalarExpression operand, int srid) {
        return Function.of("st_transform", cast(operand), IntegerLiteral.of(srid));
    }

    @Override
    public BooleanExpression bbox(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return BinaryComparisonOperation.of(cast(leftOperand), "&&", cast(rightOperand));
    }

    @Override
    public BooleanExpression contains(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return Operators.eq(Function.of("st_contains", cast(leftOperand), cast(rightOperand)), TRUE);
    }

    @Override
    public BooleanExpression crosses(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return Operators.eq(Function.of("st_crosses", cast(leftOperand), cast(rightOperand)), TRUE);
    }

    @Override
    public BooleanExpression disjoint(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return Operators.eq(Function.of("st_disjoint", cast(leftOperand), cast(rightOperand)), TRUE);
    }

    @Override
    public BooleanExpression equals(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return Operators.eq(Function.of("st_equals", cast(leftOperand), cast(rightOperand)), TRUE);
    }

    @Override
    public BooleanExpression intersects(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return Operators.eq(Function.of("st_intersects", cast(leftOperand), cast(rightOperand)), TRUE);
    }

    @Override
    public BooleanExpression overlaps(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return Operators.eq(Function.of("st_overlaps", cast(leftOperand), cast(rightOperand)), TRUE);
    }

    @Override
    public BooleanExpression touches(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return Operators.eq(Function.of("st_touches", cast(leftOperand), cast(rightOperand)), TRUE);
    }

    @Override
    public BooleanExpression within(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return Operators.eq(Function.of("st_within", cast(leftOperand), cast(rightOperand)), TRUE);
    }

    @Override
    public BooleanExpression dWithin(ScalarExpression leftOperand, ScalarExpression rightOperand, ScalarExpression distance) {
        return Operators.eq(Function.of("st_dwithin", cast(leftOperand), cast(rightOperand), distance), TRUE);
    }

    @Override
    public BooleanExpression beyond(ScalarExpression leftOperand, ScalarExpression rightOperand, ScalarExpression distance) {
        return Operators.ne(Function.of("st_dwithin", cast(leftOperand), cast(rightOperand), distance), TRUE);
    }

    private ScalarExpression cast(ScalarExpression expression) {
        return expression instanceof Placeholder ?
                Cast.of(expression, "geometry") :
                expression;
    }
}
