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

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
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

public class FilterJSONParser {
    private final FilterTextParser textParser = FilterTextParser.newInstance();
    private final GeoJSONParser geometryParser = new GeoJSONParser();

    private FilterJSONParser() {
    }

    public static FilterJSONParser newInstance() {
        return new FilterJSONParser();
    }

    public Expression parse(Object json) throws FilterParseException {
        if (json != null) {
            return json instanceof String text && JSON.isValid(text) ?
                    readExpression(JSON.parse(text)) :
                    readExpression(json);
        } else {
            return null;
        }
    }

    public <T extends Expression> T parse(Object json, Class<T> type) throws FilterParseException {
        Expression expression = parse(json);
        return type.isInstance(expression) ?
                type.cast(expression) :
                null;
    }

    private Expression readExpression(Object json) throws FilterParseException {
        if (json instanceof JSONObject object) {
            return readObject(object);
        } else if (json instanceof JSONArray array) {
            return readArray(array);
        } else {
            return readLiteral(json);
        }
    }

    private <T extends Expression> T readExpression(Object json, Class<T> type) throws FilterParseException {
        Expression expression = readExpression(json);
        if (type.isInstance(expression)) {
            return type.cast(expression);
        } else {
            Matcher matcher = Pattern.compile("(([A-Z]?[a-z]+)|([A-Z]))").matcher(type.getSimpleName());
            List<String> words = new ArrayList<>();
            while (matcher.find()) {
                words.add(matcher.group(0).toLowerCase(Locale.ROOT));
            }

            throw new FilterParseException("Failed to parse '" + json + "' as " + String.join(" ", words) + ".");
        }
    }

    private Expression readObject(JSONObject object) throws FilterParseException {
        try {
            String op = object.getString(JSONToken.OP.value());
            Object args = object.get(JSONToken.ARGS.value());
            String date = object.getString(JSONToken.DATE.value());
            String timestamp = object.getString(JSONToken.TIMESTAMP.value());
            Object interval = object.get(JSONToken.INTERVAL.value());
            Object bbox = object.get(JSONToken.BBOX.value());
            JSONToken type = JSONToken.of(object.getString(JSONToken.TYPE.value()));
            String property = object.getString(JSONToken.PROPERTY.value());

            if (op != null) {
                return readOperation(op, args instanceof JSONArray array ?
                        array :
                        new JSONArray());
            } else if (date != null) {
                return readDateLiteral(date);
            } else if (timestamp != null) {
                return readTimestampLiteral(timestamp);
            } else if (interval instanceof JSONArray array) {
                return readTimeInterval(array);
            } else if (bbox instanceof JSONArray array) {
                return readBBoxLiteral(array);
            } else if (JSONToken.GEOMETRIES.contains(type)) {
                return readGeometryLiteral(object);
            } else if (property != null) {
                return readPropertyRef(property);
            } else {
                return null;
            }
        } catch (JSONException e) {
            throw new FilterParseException("Failed to parse JSON input.", e);
        }
    }

    private Expression readOperation(String op, JSONArray args) throws FilterParseException {
        JSONToken token = JSONToken.of(op);
        if (JSONToken.BINARY_BOOLEAN_OPERATORS.contains(token)) {
            return readBinaryBooleanPredicate(token, args);
        } else if (token == JSONToken.NOT) {
            return readNotPredicate(args);
        } else if (JSONToken.BINARY_SPATIAL_OPERATORS.contains(token)) {
            return readBinarySpatialPredicate(token, args);
        } else if (JSONToken.SPATIAL_DISTANCE_OPERATORS.contains(token)) {
            return readSpatialDistancePredicate(token, args);
        } else if (JSONToken.TEMPORAL_OPERATORS.contains(token)) {
            return readTemporalPredicate(token, args);
        } else if (JSONToken.ARRAY_OPERATORS.contains(token)) {
            return readArrayPredicate(token, args);
        } else if (token == JSONToken.SQL) {
            return readSqlPredicate(args);
        } else if (JSONToken.BINARY_COMPARISON_OPERATORS.contains(token)) {
            return readBinaryComparisonPredicate(token, args);
        } else if (token == JSONToken.LIKE) {
            return readLikePredicate(args);
        } else if (token == JSONToken.BETWEEN) {
            return readBetweenPredicate(args);
        } else if (token == JSONToken.IN) {
            return readInPredicate(args);
        } else if (token == JSONToken.IS_NULL) {
            return readIsNullPredicate(args);
        } else if (JSONToken.ARITHMETIC_OPERATORS.contains(token)) {
            return readArithmeticExpression(token, args);
        } else if (token == JSONToken.UNDEFINED) {
            return readFunction(op, args);
        } else {
            throw new FilterParseException("Unsupported operator '" + op + "'.");
        }
    }

    private BinaryBooleanPredicate readBinaryBooleanPredicate(JSONToken op, JSONArray args) throws FilterParseException {
        if (args.size() > 1) {
            BooleanOperator operator = BooleanOperator.of(op);
            if (BooleanOperator.BINARY_OPERATORS.contains(operator)) {
                List<BooleanExpression> operands = new ArrayList<>();
                for (Object arg : args) {
                    operands.add(readExpression(arg, BooleanExpression.class));
                }

                return BinaryBooleanPredicate.of(operator, operands);
            } else {
                throw new FilterParseException("Invalid boolean operator '" + op + "'.");
            }
        } else {
            throw new FilterParseException("A binary boolean predicate requires two or more operands.");
        }
    }

    private BooleanExpression readNotPredicate(JSONArray args) throws FilterParseException {
        if (args.size() == 1) {
            BooleanExpression booleanExpression = readExpression(args.get(0), BooleanExpression.class);
            return booleanExpression instanceof Predicate predicate ?
                    predicate.negate() :
                    Not.of(booleanExpression);
        } else {
            throw new FilterParseException("A NOT predicate requires one operand.");
        }
    }

    private BinarySpatialPredicate readBinarySpatialPredicate(JSONToken op, JSONArray args) throws FilterParseException {
        if (args.size() == 2) {
            SpatialOperator operator = SpatialOperator.of(op);
            if (operator != null) {
                return BinarySpatialPredicate.of(
                        readExpression(args.get(0), GeometryExpression.class),
                        operator,
                        readExpression(args.get(1), GeometryExpression.class));
            } else {
                throw new FilterParseException("Invalid spatial operator '" + op + "'.");
            }
        } else {
            throw new FilterParseException("A binary spatial predicate requires two operands.");
        }
    }

    private DWithin readSpatialDistancePredicate(JSONToken op, JSONArray args) throws FilterParseException {
        if (args.size() == 3 || args.size() == 4) {
            Distance distance = Distance.of(readExpression(args.get(2), NumericLiteral.class).doubleValue());
            if (args.size() == 4) {
                StringLiteral literal = readExpression(args.get(3), StringLiteral.class);
                DistanceUnit unit = DistanceUnit.of(literal.getValue());
                if (unit != null) {
                    distance.setUnit(unit);
                } else {
                    throw new FilterParseException("Unsupported distance unit '" + literal.getValue() + "'.");
                }
            }

            return DWithin.of(
                    readExpression(args.get(0), GeometryExpression.class),
                    readExpression(args.get(1), GeometryExpression.class),
                    distance,
                    op == JSONToken.S_BEYOND);
        } else {
            throw new FilterParseException("A spatial distance predicate requires three or four operands.");
        }
    }

    private Expression readTemporalPredicate(JSONToken op, JSONArray args) throws FilterParseException {
        throw new FilterParseException("Temporal predicates are not supported.");
    }

    private Expression readArrayPredicate(JSONToken op, JSONArray args) throws FilterParseException {
        throw new FilterParseException("Array predicates are not supported.");
    }
    
    private SqlExpression readSqlPredicate(JSONArray args) throws FilterParseException {
        if (args.size() == 1) {
            return SqlExpression.of(readExpression(args.get(0), StringLiteral.class));
        } else {
            throw new FilterParseException("An SQL expression requires one operand.");
        }
    }

    private BinaryComparisonPredicate readBinaryComparisonPredicate(JSONToken op, JSONArray args) throws FilterParseException {
        if (args.size() == 2) {
            ComparisonOperator operator = ComparisonOperator.of(op);
            if (ComparisonOperator.BINARY_OPERATORS.contains(operator)) {
                return BinaryComparisonPredicate.of(
                        readExpression(args.get(0), ScalarExpression.class),
                        operator,
                        readExpression(args.get(1), ScalarExpression.class));
            } else {
                throw new FilterParseException("Invalid binary comparison operator '" + op + "'.");
            }
        } else {
            throw new FilterParseException("A binary comparison predicate requires two operands.");
        }
    }

    private Like readLikePredicate(JSONArray args) throws FilterParseException {
        if (args.size() == 2) {
            return Like.of(readExpression(args.get(0), CharacterExpression.class),
                    readExpression(args.get(1), PatternExpression.class));
        } else {
            throw new FilterParseException("Invalid number of arguments for LIKE predicate.");
        }
    }

    private Between readBetweenPredicate(JSONArray args) throws FilterParseException {
        if (args.size() == 3) {
            return Between.of(
                    readExpression(args.get(0), NumericExpression.class),
                    readExpression(args.get(1), NumericExpression.class),
                    readExpression(args.get(2), NumericExpression.class));
        } else {
            throw new FilterParseException("Invalid number of arguments for BETWEEN predicate.");
        }
    }

    private In readInPredicate(JSONArray args) throws FilterParseException {
        if (args.size() == 2
                && args.get(1) instanceof JSONArray operands) {
            List<ScalarExpression> values = new ArrayList<>();
            for (Object operand : operands) {
                values.add(readExpression(operand, ScalarExpression.class));
            }

            return In.of(
                    readExpression(args.get(0), ScalarExpression.class),
                    values);
        } else {
            throw new FilterParseException("Invalid number or types of arguments for IN predicate.");
        }
    }

    private IsNull readIsNullPredicate(JSONArray args) throws FilterParseException {
        if (args.size() == 1) {
            return IsNull.of(readExpression(args.get(0)));
        } else {
            throw new FilterParseException("An IS NULL predicate must only have one operand.");
        }
    }

    private ArithmeticExpression readArithmeticExpression(JSONToken op, JSONArray args) throws FilterParseException {
        if (args.size() == 2) {
            ArithmeticOperator operator = ArithmeticOperator.of(op);
            if (operator != null) {
                return ArithmeticExpression.of(
                        readExpression(args.get(0), NumericExpression.class),
                        operator,
                        readExpression(args.get(1), NumericExpression.class));
            } else {
                throw new FilterParseException("Invalid arithmetic operator '" + op + "'.");
            }
        } else {
            throw new FilterParseException("An arithmetic expression requires two operands.");
        }
    }

    private Function readFunction(String op, JSONArray args) throws FilterParseException {
        FunctionName name = FunctionName.of(op)
                .orElseThrow(() -> new FilterParseException("Unsupported function '" + op + "'."));

        Function function = Function.of(name);
        for (Object argument : args) {
            function.add(readExpression(argument, Argument.class));
        }

        return function;
    }

    private Expression readArray(JSONArray array) throws FilterParseException {
        throw new FilterParseException("Arrays are not supported.");
    }

    private DateLiteral readDateLiteral(String dateString) throws FilterParseException {
        try {
            return DateLiteral.of(LocalDate.parse(dateString));
        } catch (DateTimeParseException e) {
            throw new FilterParseException("Failed to parse '" + dateString + "' as date literal.", e);
        }
    }

    private TimestampLiteral readTimestampLiteral(String timestampString) throws FilterParseException {
        return TimestampLiteral.of(timestampString)
                .orElseThrow(() -> new FilterParseException("Failed to parse '" +
                        timestampString + "' as timestamp literal."));
    }

    private Expression readTimeInterval(JSONArray array) throws FilterParseException {
        throw new FilterParseException("Time interval literals are not supported.");
    }

    private GeometryLiteral readGeometryLiteral(JSONObject geometry) throws FilterParseException {
        return GeometryLiteral.of(geometryParser.parse(geometry));
    }

    private BBoxLiteral readBBoxLiteral(JSONArray bbox) throws FilterParseException {
        double[] coordinates = bbox.stream()
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .mapToDouble(Number::doubleValue)
                .toArray();

        if (coordinates.length == 4) {
            return BBoxLiteral.of(Envelope.of(
                    Coordinate.of(coordinates[0], coordinates[1]),
                    Coordinate.of(coordinates[2], coordinates[3])));
        } else if (coordinates.length == 6) {
            return BBoxLiteral.of(Envelope.of(
                    Coordinate.of(coordinates[0], coordinates[1], coordinates[2]),
                    Coordinate.of(coordinates[3], coordinates[4], coordinates[5])));
        } else {
            throw new FilterParseException("Failed to parse '" + bbox + "' as BBOX literal.");
        }
    }

    private ScalarExpression readLiteral(Object object) {
        return Literal.ofScalar(object);
    }

    private PropertyRef readPropertyRef(String property) throws FilterParseException {
        PropertyRef propertyRef = textParser.parse(property, PropertyRef.class);
        if (propertyRef != null) {
            return propertyRef;
        } else {
            throw new FilterParseException("Failed to parse '" + property + "' as property.");
        }
    }
}
