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

package org.citydb.query.filter.encoding;

import com.alibaba.fastjson2.JSONWriter;
import org.citydb.model.geometry.Coordinate;
import org.citydb.query.filter.common.Expression;
import org.citydb.query.filter.common.FilterVisitor;
import org.citydb.query.filter.common.Predicate;
import org.citydb.query.filter.common.Sign;
import org.citydb.query.filter.function.Function;
import org.citydb.query.filter.literal.*;
import org.citydb.query.filter.operation.*;

import java.util.List;

public class FilterJSONWriter {

    private FilterJSONWriter() {
    }

    public static FilterJSONWriter newInstance() {
        return new FilterJSONWriter();
    }

    public void write(Expression expression, JSONWriter jsonWriter) {
        if (expression != null) {
            expression.accept(new WriterVisitor(jsonWriter));
        }
    }

    private static class WriterVisitor implements FilterVisitor {
        private final JSONWriter jsonWriter;
        private final GeoJSONWriter geometryWriter;
        private final FilterTextWriter textWriter = FilterTextWriter.newInstance();

        WriterVisitor(JSONWriter jsonWriter) {
            this.jsonWriter = jsonWriter;
            geometryWriter = new GeoJSONWriter(jsonWriter);
        }

        @Override
        public void visit(BBoxLiteral literal) {
            Coordinate lowerCorner = literal.getValue().getLowerCorner();
            Coordinate upperCorner = literal.getValue().getUpperCorner();
            jsonWriter.startObject();
            jsonWriter.writeName(JSONToken.BBOX.value());
            jsonWriter.writeColon();
            jsonWriter.write(literal.getValue().getVertexDimension() == 2 ?
                    List.of(lowerCorner.getX(), lowerCorner.getY(),
                            upperCorner.getX(), upperCorner.getY()) :
                    List.of(lowerCorner.getX(), lowerCorner.getY(), lowerCorner.getZ(),
                            upperCorner.getX(), upperCorner.getY(), upperCorner.getZ()));
            jsonWriter.endObject();
        }

        @Override
        public void visit(BooleanLiteral literal) {
            jsonWriter.writeBool(literal.getValue());
        }

        @Override
        public void visit(DateLiteral literal) {
            jsonWriter.startObject();
            jsonWriter.writeName(JSONToken.DATE.value());
            jsonWriter.writeColon();
            jsonWriter.writeLocalDate(literal.getValue());
            jsonWriter.endObject();
        }

        @Override
        public void visit(GeometryLiteral literal) {
            geometryWriter.write(literal.getValue());
        }

        @Override
        public void visit(NumericLiteral literal) {
            if (literal.isInteger()) {
                jsonWriter.writeInt64(literal.intValue());
            } else {
                jsonWriter.writeDouble(literal.doubleValue());
            }
        }

        @Override
        public void visit(StringLiteral literal) {
            jsonWriter.writeString(literal.getValue());
        }

        @Override
        public void visit(TimestampLiteral literal) {
            jsonWriter.startObject();
            jsonWriter.writeName(JSONToken.TIMESTAMP.value());
            jsonWriter.writeColon();
            jsonWriter.writeOffsetDateTime(literal.getValue());
            jsonWriter.endObject();
        }

        @Override
        public void visit(PropertyRef propertyRef) {
            if (propertyRef.getSign() == Sign.MINUS) {
                negate(propertyRef);
            } else {
                jsonWriter.startObject();
                jsonWriter.writeName(JSONToken.PROPERTY.value());
                jsonWriter.writeColon();
                jsonWriter.writeString(textWriter.write(propertyRef));
                jsonWriter.endObject();
            }
        }

        @Override
        public void visit(Function function) {
            if (function.getSign() == Sign.MINUS) {
                negate(function);
            } else {
                startOp(function.getName().getJsonToken());
                writeList(function.getArguments(), jsonWriter::writeComma);
                endOp();
            }
        }

        @Override
        public void visit(ArithmeticExpression expression) {
            if (expression.getSign() == Sign.MINUS) {
                negate(expression);
            } else {
                startOp(expression.getOperator().getJSONToken().value());
                expression.getLeftOperand().accept(this);
                jsonWriter.writeComma();
                expression.getRightOperand().accept(this);
                endOp();
            }
        }

        @Override
        public void visit(Between between) {
            if (between.getOperator() == ComparisonOperator.NOT_BETWEEN) {
                negate(between);
            } else {
                startOp(between.getOperator().getJSONToken().value());
                between.getOperand().accept(this);
                jsonWriter.writeComma();
                between.getLowerBound().accept(this);
                jsonWriter.writeComma();
                between.getUpperBound().accept(this);
                endOp();
            }
        }

        @Override
        public void visit(BinaryBooleanPredicate predicate) {
            startOp(predicate.getOperator().getJSONToken().value());
            writeList(predicate.getOperands(), jsonWriter::writeComma);
            endOp();
        }

        @Override
        public void visit(BinaryComparisonPredicate predicate) {
            startOp(predicate.getOperator().getJSONToken().value());
            predicate.getLeftOperand().accept(this);
            jsonWriter.writeComma();
            predicate.getRightOperand().accept(this);
            endOp();
        }

        @Override
        public void visit(In in) {
            if (in.getOperator() == ComparisonOperator.NOT_IN) {
                negate(in);
            } else {
                startOp(in.getOperator().getJSONToken().value());
                in.getOperand().accept(this);
                jsonWriter.writeComma();
                jsonWriter.startArray();
                writeList(in.getValues(), jsonWriter::writeComma);
                jsonWriter.endArray();
                endOp();
            }
        }

        @Override
        public void visit(IsNull isNull) {
            if (isNull.getOperator() == ComparisonOperator.IS_NOT_NULL) {
                negate(isNull);
            } else {
                startOp(isNull.getOperator().getJSONToken().value());
                isNull.getOperand().accept(this);
                endOp();
            }
        }

        @Override
        public void visit(Like like) {
            if (like.getOperator() == ComparisonOperator.NOT_LIKE) {
                negate(like);
            } else {
                startOp(like.getOperator().getJSONToken().value());
                like.getOperand().accept(this);
                jsonWriter.writeComma();
                like.getPattern().accept(this);
                endOp();
            }
        }

        @Override
        public void visit(Not not) {
            startOp(JSONToken.NOT.value());
            not.getOperand().accept(this);
            endOp();
        }

        @Override
        public void visit(BinarySpatialPredicate predicate) {
            startOp(predicate.getOperator().getJSONToken().value());
            predicate.getLeftOperand().accept(this);
            jsonWriter.writeComma();
            predicate.getRightOperand().accept(this);
            endOp();
        }

        @Override
        public void visit(DWithin dWithin) {
            startOp(dWithin.getOperator().getJSONToken().value());
            dWithin.getLeftOperand().accept(this);
            jsonWriter.writeComma();
            dWithin.getRightOperand().accept(this);
            if (dWithin.getDistance().getValue() != 0) {
                jsonWriter.writeComma();
                jsonWriter.writeDouble(dWithin.getDistance().getValue());
                dWithin.getDistance().getUnit().ifPresent(unit -> {
                    jsonWriter.writeComma();
                    jsonWriter.writeString(unit.toString());
                });
            }
            endOp();
        }

        private void startOp(String operation) {
            jsonWriter.startObject();
            jsonWriter.writeName(JSONToken.OP.value());
            jsonWriter.writeColon();
            jsonWriter.writeString(operation);
            jsonWriter.writeName(JSONToken.ARGS.value());
            jsonWriter.writeColon();
            jsonWriter.startArray();
        }

        private void endOp() {
            jsonWriter.endArray();
            jsonWriter.endObject();
        }

        private void negate(Predicate predicate) {
            BooleanExpression negated = predicate.negate();
            if (negated instanceof Not not) {
                visit(not);
            } else {
                Not.of(negated).accept(this);
            }

            predicate.negate();
        }

        private void negate(NumericExpression expression) {
            visit(ArithmeticExpression.of(
                    expression.negate(),
                    ArithmeticOperator.MULTIPLY,
                    NumericLiteral.of(-1)));
            expression.negate();
        }

        private void writeList(List<? extends Expression> expressions, DelimiterWriter delimiter) {
            for (int i = 0; i < expressions.size(); i++) {
                expressions.get(i).accept(this);
                if (i < expressions.size() - 1) {
                    delimiter.write();
                }
            }
        }
    }

    @FunctionalInterface
    private interface DelimiterWriter {
        void write();
    }
}
