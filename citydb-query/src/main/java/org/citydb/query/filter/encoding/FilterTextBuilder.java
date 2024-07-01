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
import org.citydb.database.geometry.WKTParser;
import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.Envelope;
import org.citydb.query.filter.common.*;
import org.citydb.query.filter.function.Function;
import org.citydb.query.filter.function.FunctionName;
import org.citydb.query.filter.literal.*;
import org.citydb.query.filter.operation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FilterTextBuilder {
    private final WKTParser wktParser = new WKTParser();

    FilterTextBuilder() {
    }

    Expression build(Node node) throws FilterParseException {
        return buildExpression(node);
    }

    private Expression buildExpression(Node node) throws FilterParseException {
        return switch (node.getType()) {
            case BOOLEAN_PREDICATE -> buildBooleanPredicate(node);
            case SPATIAL_PREDICATE -> buildSpatialPredicate(node);
            case TEMPORAL_PREDICATE -> buildTemporalPredicate(node);
            case ARRAY_PREDICATE -> buildArrayPredicate(node);
            case SQL_PREDICATE -> buildSqlPredicate(node);
            case COMPARISON_PREDICATE -> buildComparisonPredicate(node);
            case ARITHMETIC_EXPRESSION -> buildArithmeticExpression(node);
            case FUNCTION -> buildFunction(node);
            case ARRAY -> buildArray(node);
            case BOOLEAN_LITERAL -> buildBooleanLiteral(node);
            case STRING_LITERAL -> buildStringLiteral(node);
            case TIME_INSTANT_LITERAL -> buildTimeInstantLiteral(node);
            case TIME_INTERVAL -> buildTimeInterval(node);
            case GEOMETRY_LITERAL -> buildGeometryLiteral(node);
            case IDENTIFIER -> buildIdentifier(node);
            case PROPERTY_REF -> buildPropertyRef(node);
            case SYNTAX -> throw new FilterParseException("Unsupported node type '" + node.getType() + "'.");
            default -> null;
        };
    }

    private <T extends Expression> T buildExpression(Node node, Class<T> type) throws FilterParseException {
        Expression expression = buildExpression(node);
        if (type.isInstance(expression)) {
            return type.cast(expression);
        } else {
            Matcher matcher = Pattern.compile("(([A-Z]?[a-z]+)|([A-Z]))").matcher(type.getSimpleName());
            List<String> words = new ArrayList<>();
            while (matcher.find()) {
                words.add(matcher.group(0).toLowerCase(Locale.ROOT));
            }

            throw new FilterParseException("Failed to parse '" + node.getToken() + "' as " +
                    String.join(" ", words) + ".");
        }
    }

    private Predicate buildBooleanPredicate(Node node) throws FilterParseException {
        if (TextToken.BINARY_BOOLEAN_OPERATORS.contains(node.getToken().getType())) {
            return buildBinaryBooleanPredicate(node);
        } else if (node.getToken().getType() == TextToken.NOT) {
            return buildNotPredicate(node);
        } else {
            throw new FilterParseException("Unsupported boolean operator '" + node.getToken() + "'.");
        }
    }

    private BinaryBooleanPredicate buildBinaryBooleanPredicate(Node node) throws FilterParseException {
        if (node.getChildren().size() == 2) {
            BooleanOperator operator = BooleanOperator.of(node.getToken().getType());
            if (BooleanOperator.BINARY_OPERATORS.contains(operator)) {
                return BinaryBooleanPredicate.of(
                        buildExpression(node.getChildren().get(0), BooleanExpression.class),
                        operator,
                        buildExpression(node.getChildren().get(1), BooleanExpression.class));
            } else {
                throw new FilterParseException("Invalid binary boolean operator '" + node.getToken() + "'.");
            }
        } else {
            throw new FilterParseException("A binary boolean predicate requires two operands.");
        }
    }

    private Not buildNotPredicate(Node node) throws FilterParseException {
        if (node.getChildren().size() == 1) {
            return Not.of(buildExpression(node.getChildren().get(0), BooleanExpression.class));
        } else {
            throw new FilterParseException("A NOT predicate requires one operand.");
        }
    }

    private SpatialPredicate buildSpatialPredicate(Node node) throws FilterParseException {
        if (TextToken.BINARY_SPATIAL_OPERATORS.contains(node.getToken().getType())) {
            return buildBinarySpatialPredicate(node);
        } else if (node.getToken().getType() == TextToken.S_DWITHIN
                || node.getToken().getType() == TextToken.S_BEYOND) {
            return buildSpatialDistancePredicate(node);
        } else {
            throw new FilterParseException("Unsupported spatial operator '" + node.getToken() + "'.");
        }
    }

    private BinarySpatialPredicate buildBinarySpatialPredicate(Node node) throws FilterParseException {
        if (node.getChildren().size() == 2) {
            SpatialOperator operator = SpatialOperator.of(node.getToken().getType());
            if (operator != null) {
                return BinarySpatialPredicate.of(
                        buildExpression(node.getChildren().get(0), GeometryExpression.class),
                        operator,
                        buildExpression(node.getChildren().get(1), GeometryExpression.class));
            } else {
                throw new FilterParseException("Invalid spatial operator '" + node.getToken() + "'.");
            }
        } else {
            throw new FilterParseException("A binary spatial predicate requires two operands.");
        }
    }

    private DWithin buildSpatialDistancePredicate(Node node) throws FilterParseException {
        if (node.getChildren().size() == 3 || node.getChildren().size() == 4) {
            Distance distance = Distance.of(
                    buildExpression(node.getChildren().get(2), NumericLiteral.class).doubleValue());
            if (node.getChildren().size() == 4) {
                StringLiteral literal = buildExpression(node.getChildren().get(3), StringLiteral.class);
                DistanceUnit unit = DistanceUnit.of(literal.getValue());
                if (unit != null) {
                    distance.setUnit(unit);
                } else {
                    throw new FilterParseException("Unsupported distance unit '" + literal.getValue() + "'.");
                }
            }

            return DWithin.of(
                    buildExpression(node.getChildren().get(0), GeometryExpression.class),
                    buildExpression(node.getChildren().get(1), GeometryExpression.class),
                    distance,
                    node.getToken().getType() == TextToken.S_BEYOND);
        } else {
            throw new FilterParseException("A spatial distance predicate requires at three or four operands.");
        }
    }

    private Expression buildTemporalPredicate(Node node) throws FilterParseException {
        throw new FilterParseException("Temporal predicates are not supported.");
    }

    private Expression buildArrayPredicate(Node node) throws FilterParseException {
        throw new FilterParseException("Array predicates are not supported.");
    }

    private SqlExpression buildSqlPredicate(Node node) throws FilterParseException {
        if (node.getChildren().size() == 1) {
            return SqlExpression.of(buildExpression(node.getChildren().get(0), StringLiteral.class));
        } else {
            throw new FilterParseException("An SQL expression requires one operand.");
        }
    }

    private ComparisonPredicate buildComparisonPredicate(Node node) throws FilterParseException {
        if (TextToken.BINARY_COMPARISON_OPERATORS.contains(node.getToken().getType())) {
            return buildBinaryComparisonPredicate(node);
        } else if (node.getToken().getType() == TextToken.LIKE
                || node.getToken().getType() == TextToken.NOT_LIKE) {
            return buildLikePredicate(node);
        } else if (node.getToken().getType() == TextToken.BETWEEN
                || node.getToken().getType() == TextToken.NOT_BETWEEN) {
            return buildBetweenPredicate(node);
        } else if (node.getToken().getType() == TextToken.IN
                || node.getToken().getType() == TextToken.NOT_IN) {
            return buildInPredicate(node);
        } else if (node.getToken().getType() == TextToken.IS_NULL
                || node.getToken().getType() == TextToken.IS_NOT_NULL) {
            return buildIsNullPredicate(node);
        } else {
            throw new FilterParseException("Unsupported comparison operator '" + node.getToken() + "'.");
        }
    }

    private BinaryComparisonPredicate buildBinaryComparisonPredicate(Node node) throws FilterParseException {
        if (node.getChildren().size() == 2) {
            ComparisonOperator operator = ComparisonOperator.of(node.getToken().getType());
            if (ComparisonOperator.BINARY_OPERATORS.contains(operator)) {
                return BinaryComparisonPredicate.of(
                        buildExpression(node.getChildren().get(0), ScalarExpression.class),
                        operator,
                        buildExpression(node.getChildren().get(1), ScalarExpression.class));
            } else {
                throw new FilterParseException("Invalid comparison operator '" + node.getToken() + "'.");
            }
        } else {
            throw new FilterParseException("A binary comparison predicate requires two operands.");
        }
    }

    private Like buildLikePredicate(Node node) throws FilterParseException {
        if (node.getChildren().size() == 2) {
            return Like.of(buildExpression(node.getChildren().get(0), CharacterExpression.class),
                    buildExpression(node.getChildren().get(1), PatternExpression.class),
                    node.getToken().getType() == TextToken.NOT_LIKE);
        } else {
            throw new FilterParseException("Invalid number of arguments for LIKE predicate.");
        }
    }

    private Between buildBetweenPredicate(Node node) throws FilterParseException {
        if (node.getChildren().size() == 3) {
            return Between.of(
                    buildExpression(node.getChildren().get(0), NumericExpression.class),
                    buildExpression(node.getChildren().get(1), NumericExpression.class),
                    buildExpression(node.getChildren().get(2), NumericExpression.class),
                    node.getToken().getType() == TextToken.NOT_BETWEEN);
        } else {
            throw new FilterParseException("Invalid number of arguments for BETWEEN predicate.");
        }
    }

    private In buildInPredicate(Node node) throws FilterParseException {
        if (node.getChildren().size() > 1) {
            List<ScalarExpression> values = new ArrayList<>();
            for (int i = 1; i < node.getChildren().size(); i++) {
                values.add(buildExpression(node.getChildren().get(i), ScalarExpression.class));
            }

            return In.of(
                    buildExpression(node.getChildren().get(0), ScalarExpression.class),
                    values,
                    node.getToken().getType() == TextToken.NOT_IN);
        } else {
            throw new FilterParseException("Invalid number of arguments for IN predicate.");
        }
    }

    private IsNull buildIsNullPredicate(Node node) throws FilterParseException {
        if (node.getChildren().size() == 1) {
            return IsNull.of(
                    buildExpression(node.getChildren().get(0)),
                    node.getToken().getType() == TextToken.IS_NOT_NULL);
        } else {
            throw new FilterParseException("An IS NULL predicate must only have one operand.");
        }
    }

    private ArithmeticExpression buildArithmeticExpression(Node node) throws FilterParseException {
        if (node.getChildren().size() == 2) {
            ArithmeticOperator operator = ArithmeticOperator.of(node.getToken().getType());
            if (operator != null) {
                return buildSign(ArithmeticExpression.of(
                                buildExpression(node.getChildren().get(0), NumericExpression.class),
                                operator,
                                buildExpression(node.getChildren().get(1), NumericExpression.class)),
                        node);
            } else {
                throw new FilterParseException("Invalid arithmetic operator '" + node.getToken() + "'.");
            }
        } else {
            throw new FilterParseException("An arithmetic expression requires two operands.");
        }
    }

    private Function buildFunction(Node node) throws FilterParseException {
        FunctionName name = FunctionName.of(node.getToken().getValue())
                .orElseThrow(() -> new FilterParseException("Unsupported function '" + node.getToken() + "'."));

        Function function = Function.of(name);
        for (Node argument : node.getChildren()) {
            function.add(buildExpression(argument, Argument.class));
        }

        return buildSign(function, node);
    }

    private Expression buildArray(Node node) throws FilterParseException {
        throw new FilterParseException("Arrays are not supported.");
    }

    private BooleanLiteral buildBooleanLiteral(Node node) {
        return node.getToken().getType() == TextToken.TRUE ?
                BooleanLiteral.TRUE :
                BooleanLiteral.FALSE;
    }

    private StringLiteral buildStringLiteral(Node node) {
        return StringLiteral.of(node.getToken().getValue());
    }

    private Literal<?> buildTimeInstantLiteral(Node node) throws FilterParseException {
        if (node.getToken().getType() == TextToken.DATE) {
            try {
                return DateLiteral.of(LocalDate.parse(node.getToken().getValue()));
            } catch (DateTimeParseException e) {
                throw new FilterParseException("Failed to parse '" + node.getToken() + "' as date literal.", e);
            }
        } else if (node.getToken().getType() == TextToken.TIMESTAMP) {
            return TimestampLiteral.of(node.getToken().getValue())
                    .orElseThrow(() -> new FilterParseException("Failed to parse '" +
                            node.getToken() + "' as timestamp literal."));
        } else {
            throw new FilterParseException("Failed to parse '" + node.getToken() + "' as time instant literal.");
        }
    }

    private Expression buildTimeInterval(Node node) throws FilterParseException {
        throw new FilterParseException("Time interval literals are not supported.");
    }

    private GeometryExpression buildGeometryLiteral(Node node) throws FilterParseException {
        try {
            if (node.getToken().getType() == TextToken.GEOMETRYCOLLECTION) {
                throw new FilterParseException("Geometry collections are not supported as literals.");
            } else if (node.getToken().getType() == TextToken.BBOX) {
                return buildBBoxLiteral(node);
            } else {
                return GeometryLiteral.of(wktParser.parse(node.getToken().getValue()));
            }
        } catch (GeometryException e) {
            throw new FilterParseException("Failed to parse '" + node.getToken() + "' as geometry literal.", e);
        }
    }

    private BBoxLiteral buildBBoxLiteral(Node node) throws FilterParseException {
        List<Double> coordinates = new ArrayList<>();
        for (Node coordinate : node.getChildren()) {
            coordinates.add(buildNumericLiteral(coordinate).doubleValue());
        }

        if (coordinates.size() == 4) {
            return BBoxLiteral.of(Envelope.of(
                    Coordinate.of(coordinates.get(0), coordinates.get(1)),
                    Coordinate.of(coordinates.get(2), coordinates.get(3))));
        } else if (coordinates.size() == 6) {
            return BBoxLiteral.of(Envelope.of(
                    Coordinate.of(coordinates.get(0), coordinates.get(1), coordinates.get(2)),
                    Coordinate.of(coordinates.get(3), coordinates.get(4), coordinates.get(5))));
        } else {
            throw new FilterParseException("Invalid number of coordinates for BBOX literal.");
        }
    }

    private NumericLiteral buildNumericLiteral(Node node) throws FilterParseException {
        return NumericLiteral.of(node.getSign().map(TextToken::toString).orElse("") + node.getToken().getValue())
                .orElseThrow(() -> new FilterParseException("Failed to parse '" +
                        node.getToken() + "' as numeric literal."));
    }

    private Expression buildIdentifier(Node node) throws FilterParseException {
        try {
            return buildNumericLiteral(node);
        } catch (FilterParseException e) {
            return buildPropertyRef(node);
        }
    }

    private PropertyRef buildPropertyRef(Node node) throws FilterParseException {
        String[] properties = node.getToken().getValue().split("\\.");
        if (properties.length > 0) {
            PropertyRef propertyRef = buildPropertyRef(properties[0]);
            PropertyRef child = propertyRef;
            for (int i = 1; i < properties.length; i++) {
                child = child.child(buildPropertyRef(properties[i]));
            }

            if (node.hasChildren()) {
                Expression filter = buildExpression(node.getChildren().get(0));
                if (filter instanceof NumericLiteral index
                        && index.isInteger()
                        && index.intValue() >= 0) {
                    filter = Function.of(FunctionName.INDEX).eq(index);
                }

                if (filter instanceof Predicate predicate) {
                    child.filter(predicate);
                    if (node.getChildren().size() == 2) {
                        child.child(buildPropertyRef(node.getChildren().get(1)));
                    }
                } else {
                    throw new FilterParseException("Failed to parse predicate of property '" +
                            child.getName().getLocalName() + "'.");
                }
            }

            return buildSign(propertyRef, node);
        } else {
            throw new FilterParseException("Invalid property name '" + node.getToken() + "'.");
        }
    }

    private PropertyRef buildPropertyRef(String identifier) throws FilterParseException {
        if (!identifier.isEmpty()) {
            return PropertyRef.of(identifier);
        } else {
            throw new FilterParseException("Invalid empty property name.");
        }
    }

    private <T extends NumericExpression> T buildSign(T expression, Node node) {
        node.getSign().ifPresent(sign -> expression.negate());
        return expression;
    }
}
