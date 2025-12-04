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

import org.citydb.sqlbuilder.function.Function;
import org.citydb.sqlbuilder.literal.IntegerLiteral;
import org.citydb.sqlbuilder.literal.ScalarExpression;
import org.citydb.sqlbuilder.literal.StringLiteral;
import org.citydb.sqlbuilder.operation.BooleanExpression;
import org.citydb.sqlbuilder.operation.Operators;

public class SpatialOperationHelper implements org.citydb.database.util.SpatialOperationHelper {
    private final StringLiteral TRUE = StringLiteral.of("1");

    @Override
    public Function extent(ScalarExpression operand) {
        return Function.of("sdo_mbr", operand);
    }

    @Override
    public Function transform(ScalarExpression operand, int srid) {
        return Function.of("MDSYS.sdo_cs.transform", operand, IntegerLiteral.of(srid));
    }

    @Override
    public BooleanExpression bbox(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return Function.of("MDSYS.sdo_filter", leftOperand, rightOperand, StringLiteral.of("querytype=WINDOW"));
    }

    @Override
    public BooleanExpression contains(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return Function.of("MDSYS.sdo_relate", leftOperand, rightOperand, StringLiteral.of("mask=CONTAINS querytype=WINDOW"));
    }

    @Override
    public BooleanExpression crosses(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        // Oracle Database does not have a direct equivalent to PostgreSQL's st_crosses function.
        // You may need to create a custom function or use a different approach.
        throw new UnsupportedOperationException("crosses is not supported in Oracle Database.");
    }

    @Override
    public BooleanExpression disjoint(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return Function.of("MDSYS.sdo_relate", leftOperand, rightOperand, StringLiteral.of("mask=DISJOINT querytype=WINDOW"));
    }

    @Override
    public BooleanExpression equals(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return Function.of("MDSYS.sdo_relate", leftOperand, rightOperand, StringLiteral.of("mask=EQUAL querytype=WINDOW"));
    }

    @Override
    public BooleanExpression intersects(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return Function.of("MDSYS.sdo_relate", leftOperand, rightOperand, StringLiteral.of("mask=ANYINTERACT querytype=WINDOW"));
    }

    @Override
    public BooleanExpression overlaps(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return Function.of("MDSYS.sdo_relate", leftOperand, rightOperand, StringLiteral.of("mask=OVERLAPBDYDISJOINT+OVERLAPBDYINTERSECT querytype=WINDOW"));
    }

    @Override
    public BooleanExpression touches(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return Function.of("MDSYS.sdo_relate", leftOperand, rightOperand, StringLiteral.of("mask=TOUCH querytype=WINDOW"));
    }

    @Override
    public BooleanExpression within(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return Function.of("MDSYS.sdo_relate", leftOperand, rightOperand, StringLiteral.of("mask=INSIDE querytype=WINDOW"));
    }

    @Override
    public BooleanExpression dWithin(ScalarExpression leftOperand, ScalarExpression rightOperand, ScalarExpression distance) {
        return Function.of("MDSYS.sdo_within_distance", leftOperand, rightOperand, StringLiteral.of("distance=" + distance.toString()));
    }

    @Override
    public BooleanExpression beyond(ScalarExpression leftOperand, ScalarExpression rightOperand, ScalarExpression distance) {
        return Operators.not(Function.of("MDSYS.sdo_within_distance", leftOperand, rightOperand, StringLiteral.of("distance=" + distance.toString())));
    }

    private ScalarExpression cast(ScalarExpression expression) {
        // Oracle Database does not require explicit casting to a geometry type.
        return expression;
    }
}