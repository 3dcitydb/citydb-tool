/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.validator;

import org.citydb.core.file.InputFile;

public interface Validator extends AutoCloseable {
    void validate(InputFile file) throws ValidateException;

    void cancel();

    @Override
    void close() throws ValidateException;
}
