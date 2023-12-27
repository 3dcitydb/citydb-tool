/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
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

import org.citydb.config.ConfigObject;
import org.citydb.config.SerializableConfig;
import org.citydb.io.reader.option.InputFormatOptions;

import java.util.Optional;
import java.util.function.Supplier;

@SerializableConfig(name = "readOptions")
public class ReadOptions {
    private boolean failFast;
    private int numberOfThreads;
    private String encoding;
    private boolean computeEnvelopes;
    private ConfigObject<InputFormatOptions> formatOptions;

    public boolean isFailFast() {
        return failFast;
    }

    public ReadOptions setFailFast(boolean failFast) {
        this.failFast = failFast;
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

    public Optional<ConfigObject<InputFormatOptions>> getFormatOptions() {
        return Optional.ofNullable(formatOptions);
    }

    public ConfigObject<InputFormatOptions> computeFormatOptionsIfAbsent(Supplier<ConfigObject<InputFormatOptions>> supplier) {
        if (formatOptions == null) {
            formatOptions = supplier.get();
        }

        return formatOptions;
    }

    public ReadOptions setFormatOptions(ConfigObject<InputFormatOptions> formatOptions) {
        this.formatOptions = formatOptions;
        return this;
    }
}
