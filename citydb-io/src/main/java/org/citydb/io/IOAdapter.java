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

package org.citydb.io;

import org.citydb.core.file.InputFile;
import org.citydb.core.file.OutputFile;
import org.citydb.io.reader.FeatureReader;
import org.citydb.io.reader.ReadException;
import org.citydb.io.reader.ReadOptions;
import org.citydb.io.validator.ValidateException;
import org.citydb.io.validator.Validator;
import org.citydb.io.writer.FeatureWriter;
import org.citydb.io.writer.WriteException;
import org.citydb.io.writer.WriteOptions;

public interface IOAdapter {
    void initialize(ClassLoader loader) throws IOAdapterException;

    default boolean canRead(InputFile file) {
        return false;
    }

    default FeatureReader createReader(InputFile file, ReadOptions options) throws ReadException {
        return null;
    }

    default FeatureWriter createWriter(OutputFile file, WriteOptions options) throws WriteException {
        return null;
    }

    default Validator createValidator() throws ValidateException {
        return null;
    }
}
