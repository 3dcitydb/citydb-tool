/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter.common;

import org.citydb.query.filter.function.Function;
import org.citydb.query.filter.literal.*;
import org.citydb.query.filter.operation.*;

public interface FilterVisitor {
    void visit(ArithmeticExpression expression);

    void visit(BBoxLiteral literal);

    void visit(Between between);

    void visit(BinaryBooleanPredicate predicate);

    void visit(BinaryComparisonPredicate predicate);

    void visit(BinarySpatialPredicate predicate);

    void visit(BooleanLiteral literal);

    void visit(DateLiteral literal);

    void visit(DWithin dWithin);

    void visit(Function function);

    void visit(GeometryLiteral literal);

    void visit(In in);

    void visit(IsNull isNull);

    void visit(Like like);

    void visit(Not not);

    void visit(NumericLiteral literal);

    void visit(PropertyRef propertyRef);

    void visit(StringLiteral literal);

    void visit(SqlExpression expression);

    void visit(TimestampLiteral literal);
}
