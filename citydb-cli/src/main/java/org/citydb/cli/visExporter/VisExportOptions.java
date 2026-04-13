/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.visExporter;

import org.citydb.config.SerializableConfig;
import org.citydb.query.Query;

import java.util.Optional;

@SerializableConfig(name = "visExportOptions")
public class VisExportOptions extends org.citydb.operation.exporter.ExportOptions {
    private Query query;

    public Optional<Query> getQuery() {
        return Optional.ofNullable(query);
    }

    public VisExportOptions setQuery(Query query) {
        this.query = query;
        return this;
    }
}
