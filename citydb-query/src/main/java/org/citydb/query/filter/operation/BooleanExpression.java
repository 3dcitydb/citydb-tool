/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter.operation;

import org.citydb.query.filter.common.Argument;

public interface BooleanExpression extends Argument {

    default BinaryBooleanPredicate and(BooleanExpression operand) {
        return this instanceof BinaryBooleanPredicate predicate ?
                predicate.fluentAnd(operand) :
                Operators.and(this, operand);
    }

    default BinaryBooleanPredicate andNot(BooleanExpression operand) {
        return and(Operators.not(operand));
    }

    default BinaryBooleanPredicate or(BooleanExpression operand) {
        return this instanceof BinaryBooleanPredicate predicate ?
                predicate.fluentOr(operand) :
                Operators.or(this, operand);
    }

    default BinaryBooleanPredicate orNot(BooleanExpression operand) {
        return or(Operators.not(operand));
    }

    default Not not() {
        return Operators.not(this);
    }
}
