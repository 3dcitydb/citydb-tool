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

public abstract class FilterWalker implements FilterVisitor {

    public void visit(Expression expression) {
    }

    @Override
    public void visit(ArithmeticExpression expression) {
        visit((Expression) expression);
        expression.getLeftOperand().accept(this);
        expression.getRightOperand().accept(this);
    }

    @Override
    public void visit(BBoxLiteral literal) {
        visit((Expression) literal);
    }

    @Override
    public void visit(Between between) {
        visit((Expression) between);
        between.getOperand().accept(this);
        between.getLowerBound().accept(this);
        between.getUpperBound().accept(this);
    }

    @Override
    public void visit(BinaryBooleanPredicate predicate) {
        visit((Expression) predicate);
        predicate.getOperands().forEach(operand -> operand.accept(this));
    }

    @Override
    public void visit(BinaryComparisonPredicate predicate) {
        visit((Expression) predicate);
        predicate.getLeftOperand().accept(this);
        predicate.getRightOperand().accept(this);
    }

    @Override
    public void visit(BinarySpatialPredicate predicate) {
        visit((Expression) predicate);
        predicate.getLeftOperand().accept(this);
        predicate.getRightOperand().accept(this);
    }

    @Override
    public void visit(BooleanLiteral literal) {
        visit((Expression) literal);
    }

    @Override
    public void visit(DateLiteral literal) {
        visit((Expression) literal);
    }

    @Override
    public void visit(DWithin dWithin) {
        visit((Expression) dWithin);
        dWithin.getLeftOperand().accept(this);
        dWithin.getRightOperand().accept(this);
    }

    @Override
    public void visit(Function function) {
        visit((Expression) function);
        function.getArguments().forEach(argument -> argument.accept(this));
    }

    @Override
    public void visit(GeometryLiteral literal) {
        visit((Expression) literal);
    }

    @Override
    public void visit(In in) {
        visit((Expression) in);
        in.getOperand().accept(this);
        in.getValues().forEach(value -> value.accept(this));
    }

    @Override
    public void visit(IsNull isNull) {
        visit((Expression) isNull);
        isNull.getOperand().accept(this);
    }

    @Override
    public void visit(Like like) {
        visit((Expression) like);
        like.getOperand().accept(this);
        like.getPattern().accept(this);
    }

    @Override
    public void visit(Not not) {
        visit((Expression) not);
        not.getOperand().accept(this);
    }

    @Override
    public void visit(NumericLiteral literal) {
        visit((Expression) literal);
    }

    @Override
    public void visit(PropertyRef propertyRef) {
        visit((Expression) propertyRef);
        propertyRef.getFilter().ifPresent(predicate -> predicate.accept(this));
        propertyRef.getChild().ifPresent(child -> child.accept(this));
    }

    @Override
    public void visit(StringLiteral literal) {
        visit((Expression) literal);
    }

    @Override
    public void visit(SqlExpression expression) {
        visit((Expression) expression);
    }

    @Override
    public void visit(TimestampLiteral literal) {
        visit((Expression) literal);
    }
}
