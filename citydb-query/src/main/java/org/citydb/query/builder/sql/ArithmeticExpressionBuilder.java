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

package org.citydb.query.builder.sql;

import org.citydb.query.builder.QueryBuildException;
import org.citydb.query.builder.common.Type;
import org.citydb.query.filter.operation.ArithmeticExpression;
import org.citydb.sqlbuilder.literal.ScalarExpression;
import org.citydb.sqlbuilder.operation.Operators;
import org.citydb.sqlbuilder.query.Select;

public class ArithmeticExpressionBuilder {
    private final FilterBuilder filterBuilder;
    private final BuilderHelper helper;

    private ArithmeticExpressionBuilder(FilterBuilder filterBuilder, BuilderHelper helper) {
        this.filterBuilder = filterBuilder;
        this.helper = helper;
    }

    static ArithmeticExpressionBuilder of(FilterBuilder filterBuilder, BuilderHelper helper) {
        return new ArithmeticExpressionBuilder(filterBuilder, helper);
    }

    BuildResult build(ArithmeticExpression expression, Select select, SqlContext context, boolean negate) throws QueryBuildException {
        BuildResult leftOperand = filterBuilder.build(expression.getLeftOperand(), select, context, negate);
        BuildResult rightOperand = filterBuilder.build(expression.getRightOperand(), select, context, negate);

        helper.cast(leftOperand, rightOperand);
        leftOperand.requireType(Type.NUMERIC_TYPES, () -> "A value of type '" + leftOperand.getType() +
                "' cannot be used as left operand of an arithmetic expression.");
        rightOperand.requireType(Type.NUMERIC_TYPES, () -> "A value of type '" + rightOperand.getType() +
                "' cannot be used as right operand of an arithmetic expression.");

        if (leftOperand.getType() != rightOperand.getType()) {
            throw new QueryBuildException("Type mismatch between operands of an arithmetic expression.");
        }

        if (leftOperand.getExpression() instanceof ScalarExpression leftExpression
                && rightOperand.getExpression() instanceof ScalarExpression rightExpression) {
            return BuildResult.of(switch (expression.getOperator()) {
                case PLUS -> Operators.plus(leftExpression, rightExpression);
                case MINUS -> Operators.minus(leftExpression, rightExpression);
                case MULTIPLY -> Operators.multiplyBy(leftExpression, rightExpression);
                case DIVIDE -> Operators.divideBy(leftExpression, rightExpression);
                case INT_DIVIDE -> helper.getOperationHelper().intDivision(leftExpression, rightExpression);
                case POWER -> helper.getOperationHelper().power(leftExpression, rightExpression);
                case MODULO -> Operators.modulo(leftExpression, rightExpression);
            }, leftOperand.getType(), leftOperand, rightOperand);
        } else {
            throw new QueryBuildException("Failed to build arithmetic expression.");
        }
    }
}
