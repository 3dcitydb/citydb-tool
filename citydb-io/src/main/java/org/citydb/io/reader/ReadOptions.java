/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.reader;

import com.alibaba.fastjson2.annotation.JSONField;
import org.citydb.config.SerializableConfig;
import org.citydb.config.common.ConfigObject;
import org.citydb.io.reader.filter.Filter;
import org.citydb.io.reader.options.ImplicitGeometryScope;
import org.citydb.io.reader.options.InputFormatOptions;

import java.nio.file.Path;
import java.util.Optional;

@SerializableConfig(name = "readOptions")
public class ReadOptions {
    private boolean failFast;
    private String tempDirectory;
    private int numberOfThreads;
    private String encoding;
    private boolean computeEnvelopes;
    private ConfigObject<InputFormatOptions> formatOptions;

    @JSONField(serialize = false, deserialize = false)
    private ImplicitGeometryScope implicitGeometryScope;
    @JSONField(serialize = false, deserialize = false)
    private Filter filter;

    public boolean isFailFast() {
        return failFast;
    }

    public ReadOptions setFailFast(boolean failFast) {
        this.failFast = failFast;
        return this;
    }

    public Optional<Path> getTempDirectory() {
        return Optional.ofNullable(tempDirectory != null ? Path.of(tempDirectory) : null);
    }

    public ReadOptions setTempDirectory(Path tempDirectory) {
        return setTempDirectory(tempDirectory != null ? tempDirectory.toString() : null);
    }

    public ReadOptions setTempDirectory(String tempDirectory) {
        this.tempDirectory = tempDirectory;
        return this;
    }

    public int getNumberOfThreads() {
        return numberOfThreads;
    }

    public ReadOptions setNumberOfThreads(int numberOfThreads) {
        this.numberOfThreads = numberOfThreads;
        return this;
    }

    public Optional<String> getEncoding() {
        return Optional.ofNullable(encoding);
    }

    public ReadOptions setEncoding(String encoding) {
        this.encoding = encoding;
        return this;
    }

    public boolean isComputeEnvelopes() {
        return computeEnvelopes;
    }

    public ReadOptions setComputeEnvelopes(boolean computeEnvelopes) {
        this.computeEnvelopes = computeEnvelopes;
        return this;
    }

    public ConfigObject<InputFormatOptions> getFormatOptions() {
        if (formatOptions == null) {
            formatOptions = new ConfigObject<>();
        }

        return formatOptions;
    }

    public ReadOptions setFormatOptions(ConfigObject<InputFormatOptions> formatOptions) {
        this.formatOptions = formatOptions;
        return this;
    }

    public ImplicitGeometryScope getImplicitGeometryScope() {
        return implicitGeometryScope != null ? implicitGeometryScope : ImplicitGeometryScope.GLOBAL;
    }

    public ReadOptions setImplicitGeometryScope(ImplicitGeometryScope implicitGeometryScope) {
        this.implicitGeometryScope = implicitGeometryScope;
        return this;
    }

    public Optional<Filter> getFilter() {
        return Optional.ofNullable(filter);
    }

    public ReadOptions setFilter(Filter filter) {
        this.filter = filter;
        return this;
    }
}
