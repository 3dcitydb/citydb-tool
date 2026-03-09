/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.util;

import org.citydb.sqlbuilder.function.Function;
import org.citydb.sqlbuilder.literal.ScalarExpression;
import org.citydb.sqlbuilder.operation.BooleanExpression;

public interface SpatialOperationHelper {
    Function extent(ScalarExpression operand);

    Function transform(ScalarExpression operand, int srid);

    BooleanExpression bbox(ScalarExpression leftOperand, ScalarExpression rightOperand);

    BooleanExpression contains(ScalarExpression leftOperand, ScalarExpression rightOperand);

    BooleanExpression crosses(ScalarExpression leftOperand, ScalarExpression rightOperand);

    BooleanExpression disjoint(ScalarExpression leftOperand, ScalarExpression rightOperand);

    BooleanExpression equals(ScalarExpression leftOperand, ScalarExpression rightOperand);

    BooleanExpression intersects(ScalarExpression leftOperand, ScalarExpression rightOperand);

    BooleanExpression overlaps(ScalarExpression leftOperand, ScalarExpression rightOperand);

    BooleanExpression touches(ScalarExpression leftOperand, ScalarExpression rightOperand);

    BooleanExpression within(ScalarExpression leftOperand, ScalarExpression rightOperand);

    BooleanExpression dWithin(ScalarExpression leftOperand, ScalarExpression rightOperand, ScalarExpression distance);

    BooleanExpression beyond(ScalarExpression leftOperand, ScalarExpression rightOperand, ScalarExpression distance);
}
