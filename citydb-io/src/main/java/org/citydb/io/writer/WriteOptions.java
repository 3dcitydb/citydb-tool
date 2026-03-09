/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.writer;

import org.citydb.config.SerializableConfig;
import org.citydb.config.common.ConfigObject;
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

    public Optional<Path> getTempDirectory() {
        return Optional.ofNullable(tempDirectory != null ? Path.of(tempDirectory) : null);
    }

    public WriteOptions setTempDirectory(Path tempDirectory) {
        return setTempDirectory(tempDirectory != null ? tempDirectory.toString() : null);
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
