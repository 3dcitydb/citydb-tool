/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.schema;

import java.util.*;

public class Join {
    private final Table table;
    private final String fromColumn;
    private final String toColumn;
    private Map<String, Condition> conditions;

    Join(Table table, String fromColumn, String toColumn, Map<String, Condition> conditions) {
        this.table = table;
        this.fromColumn = fromColumn.toLowerCase(Locale.ROOT);
        this.toColumn = toColumn.toLowerCase(Locale.ROOT);
        this.conditions = conditions;
    }

    Join(Table table, String fromColumn, String toColumn) {
        this(table, fromColumn, toColumn, null);
    }

    public Table getTable() {
        return table;
    }

    public String getFromColumn() {
        return fromColumn;
    }

    public String getToColumn() {
        return toColumn;
    }

    public Collection<Condition> getConditions() {
        return conditions != null ? conditions.values() : Collections.emptyList();
    }

    boolean hasConditionOn(String column) {
        return conditions != null && conditions.containsKey(column);
    }

    void addCondition(Condition condition) {
        if (conditions == null) {
            conditions = new LinkedHashMap<>();
        }

        conditions.put(condition.getColumn().getName(), condition);
    }

    Join postprocess(Joinable joinable, SchemaMapping schemaMapping) {
        if (table == Table.PROPERTY
                && !hasConditionOn("name")
                && !hasConditionOn("namespace_id")) {
            addCondition(new Condition(new Column("name", SimpleType.STRING),
                    joinable.getName().getLocalName()));
            addCondition(new Condition(new Column("namespace_id", SimpleType.INTEGER),
                    String.valueOf(schemaMapping.getNamespace(joinable.getName()).getId())));
        }

        return this;
    }
}
