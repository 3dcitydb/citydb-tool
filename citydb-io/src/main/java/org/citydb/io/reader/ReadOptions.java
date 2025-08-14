/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
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

package org.citydb.io.reader;

import com.alibaba.fastjson2.annotation.JSONField;
import org.citydb.config.SerializableConfig;
import org.citydb.config.common.ConfigObject;
import org.citydb.io.reader.filter.Filter;
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

    public Optional<Filter> getFilter() {
        return Optional.ofNullable(filter);
    }

    public ReadOptions setFilter(Filter filter) {
        this.filter = filter;
        return this;
    }
}
