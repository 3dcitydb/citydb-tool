/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.builder.sql;

import org.citydb.database.util.TempTable;

import java.util.Locale;
import java.util.Objects;

public class TempTableFilter {
    private final TempTable tempTable;
    private final String tempTableColumn;
    private final String featureColumn;

    private TempTableFilter(TempTable tempTable, String tempTableColumn, String featureColumn) {
        this.tempTable = Objects.requireNonNull(tempTable, "The temporary table must not be null.");
        Objects.requireNonNull(tempTableColumn, "The temporary table column must not be null.");
        Objects.requireNonNull(featureColumn, "The feature column must not be null.");

        if (!tempTable.hasColumn(tempTableColumn)) {
            throw new IllegalArgumentException("The column '" + tempTableColumn + "' is not defined for the " +
                    "temporary table " + tempTable.getQualifiedName() + ".");
        }

        this.tempTableColumn = tempTableColumn.toLowerCase(Locale.ROOT);
        this.featureColumn = featureColumn.toLowerCase(Locale.ROOT);
    }

    public static TempTableFilter of(TempTable tempTable, String column) {
        return new TempTableFilter(tempTable, column, column);
    }

    public static TempTableFilter of(TempTable tempTable, String tempTableColumn, String featureColumn) {
        return new TempTableFilter(tempTable, tempTableColumn, featureColumn);
    }

    public TempTable getTempTable() {
        return tempTable;
    }

    public String getTempTableColumn() {
        return tempTableColumn;
    }

    public String getFeatureColumn() {
        return featureColumn;
    }
}
