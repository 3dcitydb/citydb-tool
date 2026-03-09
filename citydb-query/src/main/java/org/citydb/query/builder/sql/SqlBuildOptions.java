/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.builder.sql;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public class SqlBuildOptions {
    private boolean omitDistinct;
    private Set<String> columns = new LinkedHashSet<>();

    private SqlBuildOptions() {
    }

    private SqlBuildOptions(SqlBuildOptions options) {
        omitDistinct = options.omitDistinct;
        columns = new LinkedHashSet<>(options.columns);
    }

    public static SqlBuildOptions defaults() {
        return new SqlBuildOptions();
    }

    public static SqlBuildOptions of(SqlBuildOptions options) {
        return new SqlBuildOptions(options);
    }

    public boolean isOmitDistinct() {
        return omitDistinct;
    }

    public SqlBuildOptions omitDistinct(boolean omitDistinct) {
        this.omitDistinct = omitDistinct;
        return this;
    }

    public Set<String> getColumns() {
        return columns;
    }

    public SqlBuildOptions withColumn(String column) {
        if (column != null) {
            columns.add(column.toLowerCase(Locale.ROOT));
        }

        return this;
    }

    public SqlBuildOptions withColumns(String... columns) {
        if (columns != null) {
            Arrays.stream(columns).forEach(this::withColumn);
        }

        return this;
    }
}
