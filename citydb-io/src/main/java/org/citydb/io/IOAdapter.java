/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
