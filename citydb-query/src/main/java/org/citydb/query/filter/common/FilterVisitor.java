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
