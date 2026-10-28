/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.builder.sql;

import org.citydb.database.schema.*;
import org.citydb.query.builder.QueryBuildException;
import org.citydb.query.builder.schema.Node;
import org.citydb.query.builder.schema.SchemaPathException;
import org.citydb.query.filter.common.FilterWalker;
import org.citydb.query.filter.common.Predicate;
import org.citydb.query.filter.function.FunctionName;
import org.citydb.query.filter.literal.PropertyRef;
import org.citydb.sqlbuilder.function.Function;
import org.citydb.sqlbuilder.function.WindowFunction;
import org.citydb.sqlbuilder.join.Join;
import org.citydb.sqlbuilder.join.Joins;
import org.citydb.sqlbuilder.literal.DoubleLiteral;
import org.citydb.sqlbuilder.literal.IntegerLiteral;
import org.citydb.sqlbuilder.literal.ScalarExpression;
import org.citydb.sqlbuilder.literal.StringLiteral;
import org.citydb.sqlbuilder.operation.BooleanExpression;
import org.citydb.sqlbuilder.operation.Operators;
import org.citydb.sqlbuilder.query.Select;
import org.citydb.sqlbuilder.query.Window;
import org.citydb.sqlbuilder.schema.Table;
import org.citydb.sqlbuilder.schema.WildcardColumn;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SqlContextBuilder {
    private final FilterBuilder filterBuilder;
    private final BuilderHelper helper;

    private SqlContextBuilder(FilterBuilder filterBuilder, BuilderHelper helper) {
        this.filterBuilder = filterBuilder;
        this.helper = helper;
    }

    static SqlContextBuilder of(FilterBuilder filterBuilder, BuilderHelper helper) {
        return new SqlContextBuilder(filterBuilder, helper);
    }

    SqlContext build(PropertyRef propertyRef, Select select, SqlContext context, String joinType) throws QueryBuildException {
        return build(propertyRef, select, context, joinType, false);
    }

    SqlContext build(PropertyRef propertyRef, Select select, SqlContext context, String joinType, boolean resolveValue) throws QueryBuildException {
        try {
            return build(helper.getSchemaPathBuilder()
                    .build(propertyRef, context.getSchemaPath(), resolveValue), select, context, joinType);
        } catch (SchemaPathException e) {
            throw new QueryBuildException("Failed to build property reference.", e);
        }
    }

    SqlContext build(Node node, Select select, SqlContext context, String joinType) throws QueryBuildException {
        if (node.getName().equals(context.getName())
                && node.getPredicate().isEmpty()) {
            node = node.getChild().orElse(null);
        }

        return node != null ? buildPath(node, select, context, joinType) : context;
    }

    private SqlContext buildPath(Node node, Select select, SqlContext context, String joinType) throws QueryBuildException {
        Table table = context.getTable();
        List<BooleanExpression> predicates = new ArrayList<>();
        Map<Node, List<Predicate>> nodePredicates = resolvePredicates(node);

        do {
            SqlContext subContext = context.getSubContext(node);
            if (subContext == null) {
                boolean useLateral = node.getPredicate()
                        .map(this::requiresLateralJoin)
                        .orElse(false);

                if (node.getSchemaObject() instanceof Type<?> type
                        && !helper.matches(type.getTable(), table)) {
                    throw new QueryBuildException("Expected table " + type.getTable().getName() +
                            " as context for " + type + " but found " + table.getName() + ".");
                } else if (node.getSchemaObject() instanceof GeometryObject geometry
                        && !helper.matches(geometry.getTable(), table)) {
                    throw new QueryBuildException("Expected table " + geometry.getTable().getName() +
                            " as context for " + geometry + " but found " + table.getName() + ".");
                } else if (node.getSchemaObject() instanceof Property property) {
                    table = build(property, select, table, node, joinType, useLateral, predicates);
                } else if (node.getSchemaObject() instanceof GenericAttribute attribute) {
                    table = build(attribute, select, table, joinType, useLateral);
                }

                subContext = SqlContext.of(node, table);
                context.addSubContext(node, subContext);

                if (node.getSchemaObject() instanceof Type<?> type) {
                    SchemaObject child = node.getChild()
                            .map(Node::getSchemaObject)
                            .orElse(null);
                    if (child != null
                            && (!(child instanceof Property property)
                            || !type.getDeclaredProperties().containsKey(property.getName()))) {
                        table = build(type, select, table, node, joinType, useLateral, predicates);
                    }
                }
            } else {
                table = subContext.getTable();
            }

            context = subContext;
            for (Predicate predicate : nodePredicates.getOrDefault(node, List.of())) {
                predicates.add(filterBuilder.build(predicate, select, context));
            }
        } while ((node = node.getChild().orElse(null)) != null);

        context.setPredicates(predicates);
        return context;
    }

    private Table build(Joinable joinable, Select select, Table table, Node node, String joinType, boolean useLateral,
                        List<BooleanExpression> predicates) throws QueryBuildException {
        if (joinable.getJoin().isPresent()) {
            org.citydb.database.schema.Join join = joinable.getJoin().get();
            table = build(join, table, helper.getTable(join.getTable()), select, node, joinType, useLateral, predicates);
        } else if (joinable.getJoinTable().isPresent()) {
            table = build(joinable.getJoinTable().get(), table, select, node, joinType, useLateral, predicates);
        }

        return table;
    }

    private Table build(GenericAttribute attribute, Select select, Table table, String joinType, boolean useLateral) throws QueryBuildException {
        String fromColumn = "id";
        Table toTable = helper.getTable(org.citydb.database.schema.Table.PROPERTY);
        String toColumn = helper.matches(org.citydb.database.schema.Table.PROPERTY, table)
                ? "parent_id"
                : "feature_id";

        Table joinTable = useLateral
                ? buildLateralQuery(table, fromColumn, toTable, toColumn)
                : toTable;

        Join join = Join.of(joinType, joinTable, toColumn, Operators.EQUAL_TO, table.column(fromColumn));
        List<BooleanExpression> conditions = List.of(
                Operators.eq(
                        toTable.column("name"),
                        StringLiteral.of(attribute.getName().getLocalName())),
                Operators.eq(
                        toTable.column("namespace_id"),
                        IntegerLiteral.of(helper.getNamespaceId(attribute.getName().getNamespace()))));

        conditions.forEach(condition -> {
            if (useLateral) {
                getLateralQuery(joinTable).ifPresent(lateral -> lateral.where(condition));
            } else {
                join.condition(condition);
            }
        });

        select.join(join);
        return joinTable;
    }

    private Table build(JoinTable joinTable, Table fromTable, Select select, Node node, String joinType,
                        boolean useLateral, List<BooleanExpression> predicates) throws QueryBuildException {
        if (!helper.matches(joinTable.getSourceJoin().getTable(), fromTable)) {
            throw new QueryBuildException("Expected table " + joinTable.getSourceJoin().getTable().getName() +
                    " as context for " + node + " but found " + fromTable.getName() + ".");
        }

        Table intermediateTable = helper.getTable(joinTable.getTable());
        Table toTable = helper.getTable(joinTable.getTargetJoin().getTable());

        build(joinTable.getSourceJoin(), fromTable, intermediateTable, select, node, joinType, useLateral, predicates);
        return build(joinTable.getTargetJoin(), intermediateTable, toTable, select, node, joinType, false, predicates);
    }

    private Table build(org.citydb.database.schema.Join source, Table fromTable, Table toTable, Select select,
                        Node node, String joinType, boolean useLateral, List<BooleanExpression> predicates) throws QueryBuildException {
        Table joinTable = useLateral
                ? buildLateralQuery(fromTable, source.getFromColumn(), toTable, source.getToColumn())
                : toTable;

        Join join = Join.of(joinType, joinTable, source.getToColumn(), Operators.EQUAL_TO,
                fromTable.column(source.getFromColumn()));

        for (Condition condition : source.getConditions()) {
            if (SchemaMapping.TARGET_OBJECTCLASS_ID.equals(condition.getValue())) {
                FeatureType target = node.getChild()
                        .map(Node::getSchemaObject)
                        .filter(FeatureType.class::isInstance)
                        .map(FeatureType.class::cast)
                        .orElse(FeatureType.UNDEFINED);
                if (target == FeatureType.UNDEFINED) {
                    target = node.upStream()
                            .map(Node::getSchemaObject)
                            .filter(Property.class::isInstance)
                            .findFirst()
                            .map(Property.class::cast)
                            .flatMap(Property::getTargetFeature)
                            .orElse(FeatureType.UNDEFINED);
                }

                if (target != FeatureType.UNDEFINED) {
                    BooleanExpression expression = helper.getFeatureTypesBuilder().build(target, toTable);
                    if (useLateral) {
                        getLateralQuery(joinTable).ifPresent(lateral -> lateral.where(expression));
                    } else if (joinType.equals(Joins.LEFT_JOIN)) {
                        join.condition(expression);
                    } else {
                        predicates.add(expression);
                    }
                } else {
                    throw new QueryBuildException("The schema path lacks a target feature for the " +
                            SchemaMapping.TARGET_OBJECTCLASS_ID + " token.");
                }
            } else {
                BooleanExpression expression = condition.getValue().equalsIgnoreCase("null")
                        ? Operators.isNull(toTable.column(condition.getColumn().getName()))
                        : Operators.eq(toTable.column(condition.getColumn().getName()), getLiteral(condition));
                if (useLateral) {
                    getLateralQuery(joinTable).ifPresent(lateral -> lateral.where(expression));
                } else {
                    join.condition(expression);
                }
            }
        }

        select.join(join);
        return joinTable;
    }

    private ScalarExpression getLiteral(Condition condition) throws QueryBuildException {
        ScalarExpression literal = null;
        if (condition.getColumn().getType() instanceof SimpleType type) {
            try {
                literal = switch (type) {
                    case INTEGER -> IntegerLiteral.of(Long.parseLong(condition.getValue()));
                    case DOUBLE -> DoubleLiteral.of(Double.parseDouble(condition.getValue()));
                    case STRING -> StringLiteral.of(condition.getValue());
                    default -> null;
                };
            } catch (NumberFormatException e) {
                throw new QueryBuildException("Failed to convert " + condition.getValue() + " to a " +
                        type + " literal.", e);
            }
        }

        if (literal != null) {
            return literal;
        } else {
            throw new QueryBuildException("The join condition uses an unsupported data type " +
                    condition.getColumn().getType() + ".");
        }
    }

    private Table buildLateralQuery(Table fromTable, String fromColumn, Table toTable, String toColumn) {
        Select select = Select.newInstance()
                .select(WildcardColumn.of(toTable))
                .select(WindowFunction.of(Function.of("row_number"), Window.empty()
                                .partitionBy(toTable.column(toColumn))
                                .orderBy(toTable.column("id")))
                        .as("index"))
                .from(toTable)
                .where(Operators.eq(
                        toTable.column(toColumn),
                        fromTable.column(fromColumn)));

        return Table.lateral(select);
    }

    private Optional<Select> getLateralQuery(Table table) {
        return table.getQueryExpression()
                .filter(Select.class::isInstance)
                .map(Select.class::cast);
    }

    private boolean requiresLateralJoin(Predicate predicate) {
        boolean[] requiresLateralJoin = new boolean[1];
        predicate.accept(new FilterWalker() {
            @Override
            public void visit(org.citydb.query.filter.function.Function function) {
                if (!requiresLateralJoin[0]) {
                    requiresLateralJoin[0] = function.getName() == FunctionName.INDEX;
                    super.visit(function);
                }
            }
        });

        return requiresLateralJoin[0];
    }

    private Map<Node, List<Predicate>> resolvePredicates(Node node) {
        Map<Node, List<Predicate>> nodePredicates = new IdentityHashMap<>();
        do {
            Predicate predicate = node.getPredicate().orElse(null);
            if (predicate != null) {
                Node target = requiresLateralJoin(predicate)
                        ? node
                        : getPredicateTarget(node);
                nodePredicates.computeIfAbsent(target, key -> new ArrayList<>())
                        .add(predicate);
            }
        } while ((node = node.getChild().orElse(null)) != null);

        return nodePredicates;
    }

    private Node getPredicateTarget(Node node) {
        return node.downStream()
                .skip(1)
                .dropWhile(child -> child.getSchemaObject() instanceof DataType)
                .findFirst()
                .filter(child -> child.getSchemaObject() instanceof FeatureType)
                .orElse(node);
    }
}
