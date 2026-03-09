/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.builder.sql;

import org.citydb.database.schema.FeatureType;
import org.citydb.database.schema.SchemaObject;
import org.citydb.model.common.Name;
import org.citydb.query.builder.schema.Node;
import org.citydb.sqlbuilder.operation.BooleanExpression;
import org.citydb.sqlbuilder.schema.Table;

import java.util.*;

public class SqlContext {
    private final SchemaObject object;
    private final Table table;
    private List<BooleanExpression> predicates;
    private Map<Name, SqlContext> subContexts;
    private SqlContext parent;

    private SqlContext(SchemaObject object, Table table) {
        this.object = Objects.requireNonNull(object, "The schema object must not be null.");
        this.table = Objects.requireNonNull(table, "The table must not be null.");
    }

    static SqlContext of(FeatureType featureType, BuilderHelper helper) {
        return new SqlContext(featureType, helper.getTable(featureType.getTable()));
    }

    static SqlContext of(Node node, Table table) {
        Objects.requireNonNull(node, "The node must not be null.");
        return new SqlContext(node.getSchemaObject(), table);
    }

    static SqlContext of(SqlContext other, BuilderHelper helper) {
        return new SqlContext(other.object, helper.getTable(other.table.getName()));
    }

    SchemaObject getSchemaObject() {
        return object;
    }

    Name getName() {
        return object.getName();
    }

    Table getTable() {
        return table;
    }

    Node getSchemaPath() {
        Node node = Node.of(object);
        if (parent != null) {
            Node root = node;
            SqlContext context = parent;
            do {
                Node tmp = Node.of(context.object);
                tmp.child(root);
                root = tmp;
            } while ((context = context.parent) != null);
        }

        return node;
    }

    List<BooleanExpression> getAndResetPredicates() {
        List<BooleanExpression> predicates = this.predicates != null ?
                this.predicates :
                Collections.emptyList();
        this.predicates = null;
        return predicates;
    }

    void setPredicates(List<BooleanExpression> predicates) {
        this.predicates = predicates;
    }

    SqlContext getSubContext(Node node) {
        return subContexts != null ? subContexts.get(node.getName()) : null;
    }

    void addSubContext(Node node, SqlContext context) {
        if (subContexts == null) {
            subContexts = new HashMap<>();
        }

        subContexts.put(node.getName(), context);
        context.parent = this;
    }

    Optional<SqlContext> getParent() {
        return Optional.ofNullable(parent);
    }
}
