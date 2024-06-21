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

import org.citydb.database.geometry.GeometryException;
import org.citydb.database.geometry.WKTWriter;
import org.citydb.model.geometry.Coordinate;
import org.citydb.query.filter.common.Expression;
import org.citydb.query.filter.common.FilterVisitor;
import org.citydb.query.filter.common.Predicate;
import org.citydb.query.filter.common.Sign;
import org.citydb.query.filter.function.Function;
import org.citydb.query.filter.function.FunctionName;
import org.citydb.query.filter.literal.*;
import org.citydb.query.filter.operation.*;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public class FilterTextWriter {

    private FilterTextWriter() {
    }

    public static FilterTextWriter newInstance() {
        return new FilterTextWriter();
    }

    public String write(Expression expression) {
        return write(expression, true);
    }

    private String write(Expression expression, boolean stripBrackets) {
        if (expression != null) {
            WriterVisitor visitor = new WriterVisitor();
            expression.accept(visitor);
            return stripBrackets ?
                    stripBrackets(visitor.get()) :
                    visitor.get();
        } else {
            return "";
        }
    }

    private class WriterVisitor implements FilterVisitor {
        private final StringBuilder builder = new StringBuilder();
        private final WKTWriter wktWriter = new WKTWriter().includeSRID(false);

        String get() {
            return builder.toString();
        }

        @Override
        public void visit(ArithmeticExpression expression) {
            buildSign(expression);
            builder.append("(");
            expression.getLeftOperand().accept(this);
            builder.append(" ")
                    .append(expression.getOperator().getTextToken())
                    .append(" ");
            expression.getRightOperand().accept(this);
            builder.append(")");
        }

        @Override
        public void visit(BBoxLiteral literal) {
            Coordinate lowerCorner = literal.getValue().getLowerCorner();
            Coordinate upperCorner = literal.getValue().getUpperCorner();
            List<Double> coordinates = literal.getValue().getVertexDimension() == 2 ?
                    List.of(lowerCorner.getX(), lowerCorner.getY(),
                            upperCorner.getX(), upperCorner.getY()) :
                    List.of(lowerCorner.getX(), lowerCorner.getY(), lowerCorner.getZ(),
                            upperCorner.getX(), upperCorner.getY(), upperCorner.getZ());

            builder.append(TextToken.BBOX)
                    .append("(")
                    .append(coordinates.stream().map(String::valueOf).collect(Collectors.joining(", ")))
                    .append(")");
        }

        @Override
        public void visit(Between between) {
            between.getOperand().accept(this);
            builder.append(" ")
                    .append(between.getOperator().getTextToken())
                    .append(" ");
            between.getLowerBound().accept(this);
            builder.append(" ")
                    .append(TextToken.AND)
                    .append(" ");
            between.getUpperBound().accept(this);
        }

        @Override
        public void visit(BinaryBooleanPredicate predicate) {
            builder.append("(")
                    .append(predicate.getOperands().stream()
                            .map(operand -> FilterTextWriter.this.write(operand, false))
                            .collect(Collectors.joining(" " + predicate.getOperator().getTextToken() + " ")))
                    .append(")");
        }

        @Override
        public void visit(BinaryComparisonPredicate predicate) {
            predicate.getLeftOperand().accept(this);
            builder.append(" ")
                    .append(predicate.getOperator().getTextToken())
                    .append(" ");
            predicate.getRightOperand().accept(this);
        }

        @Override
        public void visit(BinarySpatialPredicate predicate) {
            builder.append(predicate.getOperator().getTextToken());
            builder.append("(");
            predicate.getLeftOperand().accept(this);
            builder.append(", ");
            predicate.getRightOperand().accept(this);
            builder.append(")");
        }

        @Override
        public void visit(BooleanLiteral literal) {
            builder.append(literal == BooleanLiteral.TRUE ?
                    TextToken.TRUE :
                    TextToken.FALSE);
        }

        @Override
        public void visit(DateLiteral literal) {
            builder.append(TextToken.DATE)
                    .append("('")
                    .append(DateTimeFormatter.ISO_LOCAL_DATE.format(literal.getValue()))
                    .append("')");
        }

        @Override
        public void visit(DWithin dWithin) {
            builder.append(dWithin.getOperator().getTextToken());
            builder.append("(");
            dWithin.getLeftOperand().accept(this);
            builder.append(", ");
            dWithin.getRightOperand().accept(this);
            if (dWithin.getDistance().getValue() != 0) {
                builder.append(", ")
                        .append(dWithin.getDistance().getValue());
                dWithin.getDistance().getUnit().ifPresent(unit -> builder.append(", '")
                        .append(unit)
                        .append("'"));
            }
            builder.append(")");
        }

        @Override
        public void visit(Function function) {
            buildSign(function);
            builder.append(function.getName().getTextToken())
                    .append("(")
                    .append(function.getArguments().stream()
                            .map(FilterTextWriter.this::write)
                            .collect(Collectors.joining(", ")))
                    .append(")");
        }

        @Override
        public void visit(GeometryLiteral literal) {
            try {
                builder.append(wktWriter.write(literal.getValue()));
            } catch (GeometryException e) {
                throw new RuntimeException("Failed to write geometry as WKT.", e);
            }
        }

        @Override
        public void visit(In in) {
            in.getOperand().accept(this);
            builder.append(" ")
                    .append(in.getOperator().getTextToken())
                    .append(" (")
                    .append(in.getValues().stream()
                            .map(FilterTextWriter.this::write)
                            .collect(Collectors.joining(", ")))
                    .append(")");
        }

        @Override
        public void visit(IsNull isNull) {
            isNull.getOperand().accept(this);
            builder.append(" ")
                    .append(isNull.getOperator().getTextToken());
        }

        @Override
        public void visit(Like like) {
            like.getOperand().accept(this);
            builder.append(" ")
                    .append(like.getOperator().getTextToken())
                    .append(" ");
            like.getPattern().accept(this);
        }

        @Override
        public void visit(Not not) {
            builder.append(TextToken.NOT)
                    .append(" ");
            not.getOperand().accept(this);
        }

        @Override
        public void visit(NumericLiteral literal) {
            if (literal.isInteger()) {
                builder.append(literal.intValue());
            } else {
                builder.append(literal.doubleValue());
            }
        }

        @Override
        public void visit(PropertyRef propertyRef) {
            buildSign(propertyRef);

            String name = propertyRef.getName().getPrefix()
                    .map(prefix -> prefix + ":" + propertyRef.getName().getLocalName())
                    .orElse(propertyRef.getName().getLocalName());
            if (TextToken.of(name) == TextToken.UNDEFINED) {
                builder.append(name);
            } else {
                builder.append('"')
                        .append(name)
                        .append('"');
            }

            propertyRef.getTypeCast().ifPresent(typeCast ->
                    builder.append("::")
                            .append(typeCast));

            propertyRef.getFilter().ifPresent(filter -> {
                builder.append("[");
                buildStepPredicate(filter);
                builder.append("]");
            });

            propertyRef.getChild().ifPresent(child -> {
                builder.append(".");
                child.accept(this);
            });
        }

        @Override
        public void visit(StringLiteral literal) {
            builder.append("'")
                    .append(literal.getValue().replaceAll("'", "''"))
                    .append("'");
        }

        @Override
        public void visit(SqlExpression expression) {
            builder.append(TextToken.SQL)
                    .append(TextToken.L_PAREN);
            expression.getQueryExpression().accept(this);
            builder.append(TextToken.R_PAREN);
        }

        @Override
        public void visit(TimestampLiteral literal) {
            builder.append(TextToken.TIMESTAMP)
                    .append("('")
                    .append(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(literal.getValue()))
                    .append("')");
        }

        private void buildSign(NumericExpression expression) {
            if (expression.getSign() == Sign.MINUS) {
                builder.append(TextToken.MINUS);
            }
        }

        private void buildStepPredicate(Predicate predicate) {
            if (predicate instanceof BinaryComparisonPredicate comparisonPredicate
                    && comparisonPredicate.getOperator() == ComparisonOperator.EQUAL_TO
                    && comparisonPredicate.getLeftOperand() instanceof Function function
                    && function.getName() == FunctionName.INDEX
                    && function.getArguments().isEmpty()
                    && comparisonPredicate.getRightOperand() instanceof NumericLiteral index
                    && index.isInteger()
                    && index.intValue() >= 0) {
                comparisonPredicate.getRightOperand().accept(this);
            } else {
                builder.append(FilterTextWriter.this.write(predicate));
            }
        }
    }

    private String stripBrackets(String text) {
        while (text.startsWith("(") && text.endsWith(")")) {
            char[] chars = text.toCharArray();
            int count = 0;
            for (int i = 0; i < chars.length; i++) {
                if (chars[i] == '(') {
                    count++;
                } else if (chars[i] == ')'
                        && --count == 0
                        && i < chars.length - 1) {
                    return text;
                }
            }

            text = text.substring(1, text.length() - 1);
        }

        return text;
    }
}
