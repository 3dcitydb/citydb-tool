/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.deleter;

import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.annotation.JSONField;
import org.citydb.cli.common.IndexMode;
import org.citydb.config.SerializableConfig;
import org.citydb.operation.exporter.options.ValidityOptions;
import org.citydb.query.Query;

import java.util.Optional;

@SerializableConfig(name = "deleteOptions")
public class DeleteOptions extends org.citydb.operation.deleter.DeleteOptions {
    @JSONField(serializeFeatures = JSONWriter.Feature.WriteEnumUsingToString)
    private IndexMode indexMode = IndexMode.KEEP;
    private Query query;
    private ValidityOptions validityOptions;

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

    public Optional<ValidityOptions> getValidityOptions() {
        return Optional.ofNullable(validityOptions);
    }

    public DeleteOptions setValidityOptions(ValidityOptions validityOptions) {
        this.validityOptions = validityOptions;
        return this;
    }
}
