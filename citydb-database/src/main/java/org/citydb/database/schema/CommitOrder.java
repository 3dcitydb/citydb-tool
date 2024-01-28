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
