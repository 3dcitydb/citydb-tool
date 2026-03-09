/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.importer;

import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.annotation.JSONField;
import org.citydb.cli.common.IndexMode;
import org.citydb.cli.importer.options.ImportMode;
import org.citydb.config.SerializableConfig;
import org.citydb.io.reader.options.FilterOptions;

import java.util.Optional;

@SerializableConfig(name = "importOptions")
public class ImportOptions extends org.citydb.operation.importer.ImportOptions {
    @JSONField(serializeFeatures = JSONWriter.Feature.WriteEnumUsingToString)
    private ImportMode mode = ImportMode.IMPORT_ALL;
    @JSONField(serializeFeatures = JSONWriter.Feature.WriteEnumUsingToString)
    private IndexMode indexMode = IndexMode.KEEP;
    private FilterOptions filterOptions;

    public ImportMode getMode() {
        return mode != null ? mode : ImportMode.IMPORT_ALL;
    }

    public ImportOptions setMode(ImportMode mode) {
        this.mode = mode;
        return this;
    }

    public IndexMode getIndexMode() {
        return indexMode != null ? indexMode : IndexMode.KEEP;
    }

    public ImportOptions setIndexMode(IndexMode indexMode) {
        this.indexMode = indexMode;
        return this;
    }

    public Optional<FilterOptions> getFilterOptions() {
        return Optional.ofNullable(filterOptions);
    }

    public ImportOptions setFilterOptions(FilterOptions filterOptions) {
        this.filterOptions = filterOptions;
        return this;
    }
}
