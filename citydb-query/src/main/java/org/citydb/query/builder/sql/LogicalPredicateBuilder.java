/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.builder.sql;

import org.citydb.query.builder.QueryBuildException;
import org.citydb.query.filter.common.Expression;
import org.citydb.query.filter.common.LogicalPredicate;
import org.citydb.query.filter.operation.BinaryBooleanPredicate;
import org.citydb.query.filter.operation.BooleanOperator;
import org.citydb.sqlbuilder.operation.BooleanExpression;
import org.citydb.sqlbuilder.operation.Not;
import org.citydb.sqlbuilder.operation.Operators;
import org.citydb.sqlbuilder.query.Select;

import java.util.ArrayList;
import java.util.List;

public class LogicalPredicateBuilder {
    private final FilterBuilder filterBuilder;

    private LogicalPredicateBuilder(FilterBuilder filterBuilder) {
        this.filterBuilder = filterBuilder;
    }

    static LogicalPredicateBuilder of(FilterBuilder filterBuilder) {
        return new LogicalPredicateBuilder(filterBuilder);
    }

    BuildResult build(LogicalPredicate predicate, Select select, SqlContext context, boolean negate) throws QueryBuildException {
        if (predicate instanceof BinaryBooleanPredicate binaryBooleanPredicate) {
            return build(binaryBooleanPredicate, select, context, negate);
        } else if (predicate instanceof org.citydb.query.filter.operation.Not not) {
            return build(not, select, context, negate);
        } else {
            throw new QueryBuildException("Unsupported logical predicate.");
        }
    }

    private BuildResult build(BinaryBooleanPredicate predicate, Select select, SqlContext context, boolean negate) throws QueryBuildException {
        if (predicate.getOperands().size() == 1) {
            return filterBuilder.build(predicate.getOperands().get(0), select, context, negate);
        } else {
            List<BooleanExpression> operands = new ArrayList<>();
            for (Expression expression : predicate.getOperands()) {
                BuildResult operand = filterBuilder.build(expression, select, context, negate);
                operands.add(operand.build());
            }

            return predicate.getOperator() == BooleanOperator.AND ?
                    BuildResult.of(Operators.and(operands)) :
                    BuildResult.of(Operators.or(operands));
        }
    }

    private BuildResult build(org.citydb.query.filter.operation.Not not, Select select, SqlContext context, boolean negate) throws QueryBuildException {
        if (not.getOperand() instanceof BinaryBooleanPredicate) {
            BuildResult operand = filterBuilder.build(not.getOperand(), select, context, negate);
            return BuildResult.of(Not.of(operand.build()));
        } else {
            return filterBuilder.build(not.getOperand(), select, context, !negate);
        }
    }
}
