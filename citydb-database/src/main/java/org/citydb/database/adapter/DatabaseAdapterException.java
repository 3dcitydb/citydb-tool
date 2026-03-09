/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.adapter;

public class DatabaseAdapterException extends Exception {

    public DatabaseAdapterException() {
    }

    public DatabaseAdapterException(String message) {
        super(message);
    }

    public DatabaseAdapterException(Throwable cause) {
        super(cause);
    }

    public DatabaseAdapterException(String message, Throwable cause) {
        super(message, cause);
    }
}
