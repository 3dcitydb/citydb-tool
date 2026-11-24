/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.deleter;

import org.citydb.cli.common.IndexMode;
import org.citydb.config.SerializableConfig;
import org.citydb.query.Query;

import java.util.Optional;

@SerializableConfig(name = "deleteOptions")
public class DeleteOptions extends org.citydb.operation.deleter.DeleteOptions {
    private IndexMode indexMode = IndexMode.KEEP;
    private Query query;

    public IndexMode getIndexMode() {
        return indexMode != null ? indexMode : IndexMode.KEEP;
    }

    public DeleteOptions setIndexMode(IndexMode indexMode) {
        this.indexMode = indexMode;
        return this;
    }

    public Optional<Query> getQuery() {
        return Optional.ofNullable(query);
    }

    public DeleteOptions setQuery(Query query) {
        this.query = query;
        return this;
    }
}
