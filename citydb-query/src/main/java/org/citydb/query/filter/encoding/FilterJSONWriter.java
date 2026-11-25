/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter.encoding;

import org.citydb.core.json.JsonMapping;
import org.citydb.model.geometry.Coordinate;
import org.citydb.query.filter.common.Expression;
import org.citydb.query.filter.common.FilterVisitor;
import org.citydb.query.filter.common.Predicate;
import org.citydb.query.filter.common.Sign;
import org.citydb.query.filter.function.Function;
import org.citydb.query.filter.literal.*;
import org.citydb.query.filter.operation.*;
import tools.jackson.core.JsonGenerator;

import java.io.StringWriter;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class FilterJSONWriter {

    private FilterJSONWriter() {
    }

    public static FilterJSONWriter newInstance() {
        return new FilterJSONWriter();
    }

    public String write(Expression expression) {
        StringWriter writer = new StringWriter();
        try (JsonGenerator jsonWriter = JsonMapping.mapper().createGenerator(writer)) {
            write(expression, jsonWriter);
        }

        return writer.toString();
    }

    public void write(Expression expression, JsonGenerator jsonWriter) {
        if (expression != null) {
            expression.accept(new WriterVisitor(jsonWriter));
        }
    }

    private static class WriterVisitor implements FilterVisitor {
        private final JsonGenerator jsonWriter;
        private final GeoJSONWriter geometryWriter;
        private final FilterTextWriter textWriter = FilterTextWriter.newInstance();

        WriterVisitor(JsonGenerator jsonWriter) {
            this.jsonWriter = jsonWriter;
            geometryWriter = new GeoJSONWriter(jsonWriter);
        }

        @Override
        public void visit(ArithmeticExpression expression) {
            if (expression.getSign() == Sign.MINUS) {
                negate(expression);
            } else {
                startOp(expression.getOperator().getJSONToken().value());
                expression.getLeftOperand().accept(this);
                expression.getRightOperand().accept(this);
                endOp();
            }
        }

        @Override
        public void visit(BBoxLiteral literal) {
            Coordinate lowerCorner = literal.getValue().getLowerCorner();
            Coordinate upperCorner = literal.getValue().getUpperCorner();
            jsonWriter.writeStartObject();
            jsonWriter.writeName(JSONToken.BBOX.value());
            double[] values = literal.getValue().getVertexDimension() == 2
                    ? new double[]{lowerCorner.getX(), lowerCorner.getY(),
                    upperCorner.getX(), upperCorner.getY()}
                    : new double[]{lowerCorner.getX(), lowerCorner.getY(), lowerCorner.getZ(),
                    upperCorner.getX(), upperCorner.getY(), upperCorner.getZ()};
            jsonWriter.writeArray(values, 0, values.length);
            jsonWriter.writeEndObject();
        }

        @Override
        public void visit(Between between) {
            if (between.getOperator() == ComparisonOperator.NOT_BETWEEN) {
                negate(between);
            } else {
                startOp(between.getOperator().getJSONToken().value());
                between.getOperand().accept(this);
                between.getLowerBound().accept(this);
                between.getUpperBound().accept(this);
                endOp();
            }
        }

        @Override
        public void visit(BinaryBooleanPredicate predicate) {
            startOp(predicate.getOperator().getJSONToken().value());
            writeList(predicate.getOperands());
            endOp();
        }

        @Override
        public void visit(BinaryComparisonPredicate predicate) {
            startOp(predicate.getOperator().getJSONToken().value());
            predicate.getLeftOperand().accept(this);
            predicate.getRightOperand().accept(this);
            endOp();
        }

        @Override
        public void visit(BinarySpatialPredicate predicate) {
            startOp(predicate.getOperator().getJSONToken().value());
            predicate.getLeftOperand().accept(this);
            predicate.getRightOperand().accept(this);
            endOp();
        }

        @Override
        public void visit(BooleanLiteral literal) {
            jsonWriter.writeBoolean(literal.getValue());
        }

        @Override
        public void visit(DateLiteral literal) {
            jsonWriter.writeStartObject();
            jsonWriter.writeName(JSONToken.DATE.value());
            jsonWriter.writeString(literal.getValue().format(DateTimeFormatter.ISO_LOCAL_DATE));
            jsonWriter.writeEndObject();
        }

        @Override
        public void visit(DWithin dWithin) {
            startOp(dWithin.getOperator().getJSONToken().value());
            dWithin.getLeftOperand().accept(this);
            dWithin.getRightOperand().accept(this);
            if (dWithin.getDistance().getValue() != 0) {
                jsonWriter.writeNumber(dWithin.getDistance().getValue());
                dWithin.getDistance().getUnit().ifPresent(unit -> {
                    jsonWriter.writeString(unit.toString());
                });
            }
            endOp();
        }

        @Override
        public void visit(Function function) {
            if (function.getSign() == Sign.MINUS) {
                negate(function);
            } else {
                startOp(function.getName().getJsonToken());
                writeList(function.getArguments());
                endOp();
            }
        }

        @Override
        public void visit(GeometryLiteral literal) {
            geometryWriter.write(literal.getValue());
        }

        @Override
        public void visit(GeometryCollectionLiteral literal) {
            geometryWriter.writeCollection(literal.getValue().stream()
                    .map(GeometryLiteral::getValue)
                    .toList());
        }

        @Override
        public void visit(In in) {
            if (in.getOperator() == ComparisonOperator.NOT_IN) {
                negate(in);
            } else {
                startOp(in.getOperator().getJSONToken().value());
                in.getOperand().accept(this);
                jsonWriter.writeStartArray();
                writeList(in.getValues());
                jsonWriter.writeEndArray();
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
        public void visit(NumericLiteral literal) {
            if (literal.isInteger()) {
                jsonWriter.writeNumber(literal.intValue());
            } else {
                jsonWriter.writeNumber(literal.doubleValue());
            }
        }

        @Override
        public void visit(PropertyRef propertyRef) {
            if (propertyRef.getSign() == Sign.MINUS) {
                negate(propertyRef);
            } else {
                jsonWriter.writeStartObject();
                jsonWriter.writeName(JSONToken.PROPERTY.value());
                jsonWriter.writeString(textWriter.write(propertyRef));
                jsonWriter.writeEndObject();
            }
        }

        @Override
        public void visit(StringLiteral literal) {
            jsonWriter.writeString(literal.getValue());
        }

        @Override
        public void visit(SqlExpression expression) {
            startOp(JSONToken.SQL.value());
            expression.getQueryExpression().accept(this);
            endOp();
        }

        @Override
        public void visit(TimestampLiteral literal) {
            jsonWriter.writeStartObject();
            jsonWriter.writeName(JSONToken.TIMESTAMP.value());
            jsonWriter.writeString(literal.getValue().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            jsonWriter.writeEndObject();
        }

        private void startOp(String operation) {
            jsonWriter.writeStartObject();
            jsonWriter.writeName(JSONToken.OP.value());
            jsonWriter.writeString(operation);
            jsonWriter.writeName(JSONToken.ARGS.value());
            jsonWriter.writeStartArray();
        }

        private void endOp() {
            jsonWriter.writeEndArray();
            jsonWriter.writeEndObject();
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

        private void writeList(List<? extends Expression> expressions) {
            for (Expression expression : expressions) {
                expression.accept(this);
            }
        }
    }
}
