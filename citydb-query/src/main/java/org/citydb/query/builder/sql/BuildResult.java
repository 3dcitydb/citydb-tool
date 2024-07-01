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

package org.citydb.query.builder.sql;

import org.citydb.database.schema.*;
import org.citydb.query.builder.QueryBuildException;
import org.citydb.query.builder.common.Type;
import org.citydb.query.filter.literal.BooleanLiteral;
import org.citydb.query.filter.literal.NumericLiteral;
import org.citydb.query.filter.literal.PropertyRef;
import org.citydb.query.filter.literal.StringLiteral;
import org.citydb.sqlbuilder.common.Expression;
import org.citydb.sqlbuilder.literal.Placeholder;
import org.citydb.sqlbuilder.literal.ScalarExpression;
import org.citydb.sqlbuilder.operation.BooleanExpression;
import org.citydb.sqlbuilder.operation.Operators;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class BuildResult {
    private final PropertyRef propertyRef;
    private SqlContext context;
    private Expression expression;
    private Type type;
    private List<BooleanExpression> predicates;

    private BuildResult(PropertyRef propertyRef, Expression expression, Type type, List<BooleanExpression> predicates) {
        this.propertyRef = propertyRef;
        this.expression = expression;
        this.type = type;
        this.predicates = predicates;
    }

    static BuildResult of(Expression expression, Type type, List<BuildResult> operands) {
        return new BuildResult(null, expression, type, operands != null ?
                operands.stream()
                        .map(BuildResult::getAndResetPredicates)
                        .flatMap(Collection::stream)
                        .toList() :
                null);
    }

    static BuildResult of(Expression expression, Type type, BuildResult... operands) {
        return of(expression, type, operands != null ? Arrays.asList(operands) : null);
    }

    static BuildResult of(Expression expression, Type type) {
        return new BuildResult(null, expression, type, null);
    }

    static BuildResult of(BooleanExpression expression, List<BuildResult> operands) {
        return of(expression, Type.BOOLEAN, operands);
    }

    static BuildResult of(BooleanExpression expression, BuildResult... operands) {
        return of(expression, Type.BOOLEAN, operands);
    }

    static BuildResult of(BooleanExpression expression) {
        return of(expression, Type.BOOLEAN);
    }

    static BuildResult of(PropertyRef propertyRef) {
        return new BuildResult(propertyRef, null, Type.UNDEFINED, null);
    }

    boolean isSetPropertyRef() {
        return propertyRef != null;
    }

    PropertyRef getPropertyRef() {
        return propertyRef;
    }

    boolean isSetExpression() {
        return expression != null;
    }

    Expression getExpression() {
        return expression;
    }

    Type getType() {
        return type != null ? type : Type.UNDEFINED;
    }

    void requireType(Type type, Supplier<String> message) throws QueryBuildException {
        requireType(EnumSet.of(type), message);
    }

    void requireType(EnumSet<Type> types, Supplier<String> message) throws QueryBuildException {
        if (type == Type.UNDEFINED && !types.contains(Type.UNDEFINED)) {
            String hint = types.stream()
                    .map(type -> "'" + type + "'")
                    .collect(Collectors.joining(", "));
            throw new QueryBuildException("Found untyped attribute '" + getName() + "' but expected a " +
                    hint + ". Use explicit type casts.");
        } else if (!types.contains(type)) {
            throw new QueryBuildException(message.get());
        }
    }

    BuildResult update(SqlContext context) {
        this.context = context;
        if (context != null) {
            predicates = context.getAndResetPredicates();
            if (context.getSchemaObject() instanceof ValueObject valueObject) {
                Column column = valueObject.getValue().flatMap(Value::getColumn).orElse(null);
                if (column != null) {
                    update(context.getTable().column(column.getName()), Type.of(column.getType()));
                } else if (valueObject.getName().equals(
                        org.citydb.model.property.DataType.FEATURE_PROPERTY.getName())) {
                    type = Type.FEATURE;
                } else if (valueObject.getName().equals(
                        org.citydb.model.property.DataType.IMPLICIT_GEOMETRY_PROPERTY.getName())) {
                    type = Type.IMPLICIT_GEOMETRY;
                }
            }
        }

        return this;
    }

    void cast(Type target, BuilderHelper helper) throws QueryBuildException {
        if (type != target) {
            if (type == Type.UNDEFINED
                    && context != null
                    && context.getSchemaObject() instanceof GenericAttribute attribute) {
                if (target == Type.STRING) {
                    DataType type = target.getSchemaType(helper.getSchemaMapping());
                    if (helper.matches(type.getTable(), context.getTable())) {
                        type.getValue()
                                .flatMap(Value::getColumn)
                                .ifPresent(column -> update(context.getTable().column(column.getName()),
                                        Type.of(column.getType())));
                    }
                } else {
                    throw new QueryBuildException("Cannot cast '" + attribute.getName() + "' to '" + target + "'. " +
                            "Use explicit type casts.");
                }
            }

            if (expression instanceof Placeholder placeholder) {
                Object value = placeholder.getValue()
                        .orElseThrow(() -> new QueryBuildException("No value provided for literal placeholder."));
                if (target == Type.BOOLEAN) {
                    switch (type) {
                        case INTEGER:
                        case DOUBLE:
                        case STRING:
                            update(Placeholder.of(BooleanLiteral.of(value).getValue()), Type.BOOLEAN);
                    }
                } else if (target == Type.INTEGER) {
                    NumericLiteral.of(value)
                            .filter(NumericLiteral::isInteger)
                            .ifPresent(literal -> update(Placeholder.of(literal.intValue()), Type.INTEGER));
                } else if (target == Type.DOUBLE) {
                    if (type == Type.INTEGER) {
                        type = Type.DOUBLE;
                    } else {
                        NumericLiteral.of(value)
                                .ifPresent(literal -> update(Placeholder.of(literal.doubleValue()), Type.DOUBLE));
                    }
                } else if (target == Type.STRING) {
                    update(Placeholder.of(StringLiteral.of(value).getValue()), Type.STRING);
                }
            } else if (target == Type.DOUBLE && type == Type.INTEGER) {
                type = Type.DOUBLE;
            }

            if (expression instanceof ScalarExpression scalarExpression
                    && type == Type.TIMESTAMP
                    && target == Type.DATE) {
                update(helper.getOperationHelper().toDate(scalarExpression), Type.DATE);
            }
        }
    }

    BooleanExpression build() throws QueryBuildException {
        BooleanExpression predicate;
        if (expression instanceof BooleanExpression booleanExpression) {
            predicate = booleanExpression;
        } else if (expression instanceof Placeholder placeholder
                && placeholder.getValue().orElse(null) instanceof Boolean value) {
            predicate = org.citydb.sqlbuilder.literal.BooleanLiteral.of(value);
        } else {
            throw new QueryBuildException("Failed to build '" + getName() + "' as boolean expression.");
        }

        return predicates == null || predicates.isEmpty() ?
                predicate :
                Operators.and(getAndResetPredicates()).add(predicate);
    }

    private void update(Expression expression, Type type) {
        this.expression = expression;
        this.type = type;
    }

    private String getName() {
        if (propertyRef != null) {
            return propertyRef.last().getName().toString();
        } else if (context != null) {
            return context.getName().toString();
        } else if (expression instanceof Placeholder placeholder
                && placeholder.getValue().isPresent()) {
            return placeholder.getValue().get().getClass().getSimpleName();
        } else if (expression != null) {
            return expression.getClass().getSimpleName();
        } else {
            return "";
        }
    }

    private List<BooleanExpression> getAndResetPredicates() {
        List<BooleanExpression> predicates = this.predicates != null ?
                this.predicates :
                Collections.emptyList();
        this.predicates = null;
        return predicates;
    }
}
