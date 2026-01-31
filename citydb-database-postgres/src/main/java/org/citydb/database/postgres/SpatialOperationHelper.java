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

import org.citydb.core.version.Version;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.schema.Table;
import org.citydb.sqlbuilder.function.Cast;
import org.citydb.sqlbuilder.function.Function;
import org.citydb.sqlbuilder.literal.IntegerLiteral;
import org.citydb.sqlbuilder.literal.Placeholder;
import org.citydb.sqlbuilder.literal.ScalarExpression;
import org.citydb.sqlbuilder.literal.StringLiteral;
import org.citydb.sqlbuilder.operation.BinaryComparisonOperation;
import org.citydb.sqlbuilder.operation.BooleanExpression;
import org.citydb.sqlbuilder.operation.Operators;
import org.citydb.sqlbuilder.schema.Column;

public class SpatialOperationHelper implements org.citydb.database.util.SpatialOperationHelper {
    private final StringLiteral TRUE = StringLiteral.of("TRUE");
    private final PostgresqlAdapter adapter;

    SpatialOperationHelper(DatabaseAdapter adapter) {
        this.adapter = (PostgresqlAdapter) adapter;
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
        return build("st_contains", leftOperand, rightOperand);
    }

    @Override
    public BooleanExpression crosses(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return build("st_crosses", leftOperand, rightOperand);
    }

    @Override
    public BooleanExpression disjoint(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return build("st_disjoint", leftOperand, rightOperand);
    }

    @Override
    public BooleanExpression equals(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return build("st_equals", leftOperand, rightOperand);
    }

    @Override
    public BooleanExpression intersects(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return adapter.hasSFCGALSupport() ?
                Operators.eq(Function.of("st_3dintersects", cast(leftOperand), cast(rightOperand)), TRUE) :
                build("st_intersects", leftOperand, rightOperand);
    }

    @Override
    public BooleanExpression overlaps(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return build("st_overlaps", leftOperand, rightOperand);
    }

    @Override
    public BooleanExpression touches(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return build("st_touches", leftOperand, rightOperand);
    }

    @Override
    public BooleanExpression within(ScalarExpression leftOperand, ScalarExpression rightOperand) {
        return build("st_within", leftOperand, rightOperand);
    }

    @Override
    public BooleanExpression dWithin(ScalarExpression leftOperand, ScalarExpression rightOperand, ScalarExpression distance) {
        return adapter.hasSFCGALSupport() ?
                Operators.eq(Function.of("st_3ddwithin", cast(leftOperand), cast(rightOperand), distance), TRUE) :
                build(leftOperand, rightOperand, distance);
    }

    @Override
    public BooleanExpression beyond(ScalarExpression leftOperand, ScalarExpression rightOperand, ScalarExpression distance) {
        return Operators.not(dWithin(leftOperand, rightOperand, distance));
    }

    private BooleanExpression build(String operationName, ScalarExpression leftOperand, ScalarExpression rightOperand) {
        BooleanExpression expression = Operators.eq(Function.of(operationName,
                prepare(leftOperand), prepare(rightOperand)), TRUE);
        return requiresNormalization(leftOperand) || requiresNormalization(rightOperand) ?
                Operators.and(bbox(leftOperand, rightOperand), expression) :
                expression;
    }

    private BooleanExpression build(ScalarExpression leftOperand, ScalarExpression rightOperand, ScalarExpression distance) {
        BooleanExpression dWithin = Operators.eq(Function.of("st_dwithin",
                prepare(leftOperand), prepare(rightOperand), distance), TRUE);
        return requiresNormalization(leftOperand) || requiresNormalization(rightOperand) ?
                Operators.and(bbox(leftOperand,
                        Function.of("st_expand", Function.of("box2d", cast(rightOperand)), distance)), dWithin) :
                dWithin;
    }

    private ScalarExpression prepare(ScalarExpression expression) {
        return normalize(cast(expression));
    }

    private ScalarExpression cast(ScalarExpression expression) {
        return expression instanceof Placeholder ?
                Cast.of(expression, "geometry") :
                expression;
    }

    private ScalarExpression normalize(ScalarExpression expression) {
        if (requiresNormalization(expression)) {
            return adapter.getDatabaseMetadata().getVersion().compareTo(Version.of(5, 1, 1)) < 0 ?
                    Function.of("st_forcecollection", expression) :
                    Function.of("citydb_pkg.normalize_polyhedral", expression);
        } else {
            return expression;
        }
    }

    private boolean requiresNormalization(ScalarExpression expression) {
        return expression instanceof Column column
                && Table.GEOMETRY_DATA.getName().equals(column.getTable().getName());
    }
}
