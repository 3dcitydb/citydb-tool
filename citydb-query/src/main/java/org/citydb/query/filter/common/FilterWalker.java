/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
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

    @Override
    public void visit(Between between) {
        between.getOperand().accept(this);
        between.getLowerBound().accept(this);
        between.getUpperBound().accept(this);
    }

    @Override
    public void visit(BBoxLiteral literal) {
    }

    @Override
    public void visit(BooleanLiteral literal) {
    }

    @Override
    public void visit(DateLiteral literal) {
    }

    @Override
    public void visit(GeometryLiteral literal) {
    }

    @Override
    public void visit(NumericLiteral literal) {
    }

    @Override
    public void visit(StringLiteral literal) {
    }

    @Override
    public void visit(TimestampLiteral literal) {
    }

    @Override
    public void visit(PropertyRef propertyRef) {
        propertyRef.getFilter().ifPresent(predicate -> predicate.accept(this));
        propertyRef.getChild().ifPresent(child -> child.accept(this));
    }

    @Override
    public void visit(Function function) {
        function.getArguments().forEach(argument -> argument.accept(this));
    }

    @Override
    public void visit(ArithmeticExpression expression) {
        expression.getLeftOperand().accept(this);
        expression.getRightOperand().accept(this);
    }

    @Override
    public void visit(BinaryBooleanPredicate predicate) {
        predicate.getOperands().forEach(operand -> operand.accept(this));
    }

    @Override
    public void visit(BinaryComparisonPredicate predicate) {
        predicate.getLeftOperand().accept(this);
        predicate.getRightOperand().accept(this);
    }

    @Override
    public void visit(In in) {
        in.getOperand().accept(this);
        in.getValues().forEach(value -> value.accept(this));
    }

    @Override
    public void visit(IsNull isNull) {
        isNull.getOperand().accept(this);
    }

    @Override
    public void visit(Like like) {
        like.getOperand().accept(this);
        like.getPattern().accept(this);
    }

    @Override
    public void visit(Not not) {
        not.getOperand().accept(this);
    }

    @Override
    public void visit(SpatialPredicate predicate) {
        predicate.getLeftOperand().accept(this);
        predicate.getRightOperand().accept(this);
    }
}
