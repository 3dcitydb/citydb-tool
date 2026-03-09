/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.schema;

import java.util.*;
import java.util.stream.Collectors;

public class CommitOrder {
    private final List<Table> commitOrder = new ArrayList<>();
    private final Map<Table, List<Table>> commitOrderByTable = new EnumMap<>(Table.class);

    private CommitOrder(Collection<Table> tables) {
        if (tables != null) {
            computeCommitOrder(new HashSet<>(tables));
        }
    }

    public static CommitOrder newInstance() {
        return new CommitOrder(Arrays.asList(Table.values()));
    }

    public static CommitOrder of(Table... tables) {
        return new CommitOrder(tables != null ? Arrays.asList(tables) : Collections.emptyList());
    }

    public static CommitOrder of(Collection<Table> tables) {
        return new CommitOrder(tables);
    }

    public List<Table> getCommitOrder() {
        return commitOrder;
    }

    public List<Table> getCommitOrder(Table table) {
        return commitOrderByTable.getOrDefault(table, Collections.emptyList());
    }

    private void computeCommitOrder(Set<Table> tables) {
        tables.forEach(table -> computeCommitOrder(table, tables));
        commitOrder.forEach(table -> commitOrderByTable.put(table, computeCommitOrder(table)));
    }

    private void computeCommitOrder(Table table, Set<Table> tables) {
        if (!commitOrder.contains(table)) {
            table.getDependencies(false).stream()
                    .filter(tables::contains)
                    .forEach(dependency -> computeCommitOrder(dependency, tables));
            commitOrder.add(table);
        }
    }

    private List<Table> computeCommitOrder(Table table) {
        List<Table> commitOrder = this.commitOrder.stream()
                .filter(table::dependsOn)
                .collect(Collectors.toList());
        commitOrder.add(table);
        return commitOrder;
    }
}
