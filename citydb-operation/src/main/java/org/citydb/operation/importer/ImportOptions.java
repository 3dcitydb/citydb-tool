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

package org.citydb.operation.importer;

import org.citydb.config.SerializableConfig;
import org.citydb.core.CoreConstants;

import java.nio.file.Path;

@SerializableConfig(name = "importOptions")
public class ImportOptions {
    public static final int DEFAULT_BATCH_SIZE = 20;

    private String tempDirectory;
    private int numberOfThreads;
    private int batchSize = DEFAULT_BATCH_SIZE;

    public Path getTempDirectory() {
        return tempDirectory != null ? CoreConstants.WORKING_DIR.resolve(tempDirectory) : null;
    }

    public ImportOptions setTempDirectory(String tempDirectory) {
        this.tempDirectory = tempDirectory;
        return this;
    }

    public int getNumberOfThreads() {
        return numberOfThreads;
    }

    public ImportOptions setNumberOfThreads(int numberOfThreads) {
        this.numberOfThreads = numberOfThreads;
        return this;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public ImportOptions setBatchSize(int batchSize) {
        this.batchSize = batchSize;
        return this;
    }
}
