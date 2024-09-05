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

package org.citydb.io.writer;

import org.citydb.config.SerializableConfig;
import org.citydb.config.common.ConfigObject;
import org.citydb.core.CoreConstants;
import org.citydb.io.writer.options.OutputFormatOptions;

import java.nio.file.Path;
import java.util.Optional;

@SerializableConfig(name = "writeOptions")
public class WriteOptions {
    private boolean failFast;
    private String tempDirectory;
    private int numberOfThreads;
    private String encoding;
    private String srsName;
    private ConfigObject<OutputFormatOptions> formatOptions;

    public boolean isFailFast() {
        return failFast;
    }

    public WriteOptions setFailFast(boolean failFast) {
        this.failFast = failFast;
        return this;
    }

    public Path getTempDirectory() {
        return tempDirectory != null ? CoreConstants.WORKING_DIR.resolve(tempDirectory) : null;
    }

    public WriteOptions setTempDirectory(String tempDirectory) {
        this.tempDirectory = tempDirectory;
        return this;
    }

    public int getNumberOfThreads() {
        return numberOfThreads;
    }

    public WriteOptions setNumberOfThreads(int numberOfThreads) {
        this.numberOfThreads = numberOfThreads;
        return this;
    }

    public Optional<String> getEncoding() {
        return Optional.ofNullable(encoding);
    }

    public WriteOptions setEncoding(String encoding) {
        this.encoding = encoding;
        return this;
    }

    public Optional<String> getSrsName() {
        return Optional.ofNullable(srsName);
    }

    public WriteOptions setSrsName(String srsName) {
        this.srsName = srsName;
        return this;
    }

    public ConfigObject<OutputFormatOptions> getFormatOptions() {
        if (formatOptions == null) {
            formatOptions = new ConfigObject<>();
        }

        return formatOptions;
    }

    public WriteOptions setFormatOptions(ConfigObject<OutputFormatOptions> formatOptions) {
        this.formatOptions = formatOptions;
        return this;
    }
}
