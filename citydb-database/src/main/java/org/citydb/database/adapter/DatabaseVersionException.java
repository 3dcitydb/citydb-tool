/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.adapter;

import org.citydb.database.DatabaseException;

public class DatabaseVersionException extends DatabaseException {

    public DatabaseVersionException() {
    }

    public DatabaseVersionException(String message) {
        super(message);
    }

    public DatabaseVersionException(Throwable cause) {
        super(cause);
    }

    public DatabaseVersionException(String message, Throwable cause) {
        super(message, cause);
    }
}
