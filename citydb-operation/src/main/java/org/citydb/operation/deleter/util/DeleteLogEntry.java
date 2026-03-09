/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.deleter.util;

import org.citydb.database.schema.Table;

public class DeleteLogEntry {
    private final Table table;
    private final long databaseId;
    private boolean committed;

    private DeleteLogEntry(Table table, long databaseId) {
        this.table = table;
        this.databaseId = databaseId;
    }

    public static DeleteLogEntry of(Table table, long databaseId) {
        return new DeleteLogEntry(table, databaseId);
    }

    public Table getTable() {
        return table;
    }

    public long getDatabaseId() {
        return databaseId;
    }

    public boolean isCommitted() {
        return committed;
    }

    public DeleteLogEntry setCommitted(boolean committed) {
        this.committed = committed;
        return this;
    }
}
