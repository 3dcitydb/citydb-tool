/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.exporter;

import org.citydb.config.SerializableConfig;
import org.citydb.query.Query;
import org.citydb.util.tiling.Tiling;

import java.util.Optional;

@SerializableConfig(name = "exportOptions")
public class ExportOptions extends org.citydb.operation.exporter.ExportOptions {
    private Query query;
    private Tiling tiling;

    public Optional<Query> getQuery() {
        return Optional.ofNullable(query);
    }

    public ExportOptions setQuery(Query query) {
        this.query = query;
        return this;
    }

    public Optional<Tiling> getTiling() {
        return Optional.ofNullable(tiling);
    }

    public ExportOptions setTiling(Tiling tiling) {
        this.tiling = tiling;
        return this;
    }
}
