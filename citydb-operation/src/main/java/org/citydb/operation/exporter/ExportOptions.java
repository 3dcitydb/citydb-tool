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

package org.citydb.operation.exporter;

import com.alibaba.fastjson2.annotation.JSONField;
import org.citydb.config.SerializableConfig;
import org.citydb.config.common.SrsReference;
import org.citydb.core.concurrent.LazyCheckedInitializer;
import org.citydb.core.file.OutputFile;
import org.citydb.core.file.output.RegularOutputFile;
import org.citydb.operation.exporter.options.AppearanceOptions;
import org.citydb.operation.exporter.options.LodOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@SerializableConfig(name = "exportOptions")
public class ExportOptions {
    @JSONField(serialize = false, deserialize = false)
    private final LazyCheckedInitializer<OutputFile, IOException> tempOutputFile = LazyCheckedInitializer.of(
            () -> new RegularOutputFile(Files.createTempDirectory("citydb-").resolve("output.tmp")));

    @JSONField(serialize = false, deserialize = false)
    private OutputFile outputFile;
    private int numberOfThreads;
    private SrsReference targetSrs;
    private LodOptions lodOptions;
    private boolean exportAppearances = true;
    private AppearanceOptions appearanceOptions;

    public OutputFile getOutputFile() {
        if (outputFile == null) {
            try {
                outputFile = tempOutputFile.get();
            } catch (IOException e) {
                throw new RuntimeException("Failed to create temporary output directory.", e);
            }
        }

        return outputFile;
    }

    public ExportOptions setOutputFile(OutputFile outputFile) {
        this.outputFile = outputFile;
        return this;
    }

    public ExportOptions setOutputDirectory(Path outputDirectory) {
        outputFile = new RegularOutputFile(outputDirectory.resolve("output.tmp"));
        return this;
    }

    public int getNumberOfThreads() {
        return numberOfThreads;
    }

    public ExportOptions setNumberOfThreads(int numberOfThreads) {
        this.numberOfThreads = numberOfThreads;
        return this;
    }

    public Optional<SrsReference> getTargetSrs() {
        return Optional.ofNullable(targetSrs);
    }

    public ExportOptions setTargetSrs(SrsReference targetSrs) {
        this.targetSrs = targetSrs;
        return this;
    }

    public Optional<LodOptions> getLodOptions() {
        return Optional.ofNullable(lodOptions);
    }

    public ExportOptions setLodOptions(LodOptions lodOptions) {
        this.lodOptions = lodOptions;
        return this;
    }

    public boolean isExportAppearances() {
        return exportAppearances;
    }

    public ExportOptions setExportAppearances(boolean exportAppearances) {
        this.exportAppearances = exportAppearances;
        return this;
    }

    public Optional<AppearanceOptions> getAppearanceOptions() {
        return Optional.ofNullable(appearanceOptions);
    }

    public ExportOptions setAppearanceOptions(AppearanceOptions appearanceOptions) {
        this.appearanceOptions = appearanceOptions;
        return this;
    }
}
