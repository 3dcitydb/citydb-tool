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

package org.citydb.operation.exporter;

import com.alibaba.fastjson2.annotation.JSONField;
import org.citydb.config.SerializableConfig;
import org.citydb.core.concurrent.LazyInitializer;
import org.citydb.core.file.OutputFile;
import org.citydb.core.file.output.RegularOutputFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@SerializableConfig(name = "exportOptions")
public class ExportOptions {
    private final LazyInitializer<OutputFile, IOException> tempOutputFile = LazyInitializer.of(
            () -> new RegularOutputFile(Files.createTempDirectory("citydb-").resolve("output.tmp")));

    @JSONField(serialize = false, deserialize = false)
    private OutputFile outputFile;
    private int numberOfThreads;
    private int numberOfTextureBuckets;

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
        if (numberOfThreads > 0) {
            this.numberOfThreads = numberOfThreads;
        }

        return this;
    }

    public int getNumberOfTextureBuckets() {
        return numberOfTextureBuckets;
    }

    public ExportOptions setNumberOfTextureBuckets(int numberOfTextureBuckets) {
        if (numberOfTextureBuckets > 0) {
            this.numberOfTextureBuckets = numberOfTextureBuckets;
        }

        return this;
    }
}
