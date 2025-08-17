/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
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
