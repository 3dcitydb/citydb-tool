/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io;

public class IOAdapterException extends Exception {

    public IOAdapterException() {
    }

    public IOAdapterException(String message) {
        super(message);
    }

    public IOAdapterException(Throwable cause) {
        super(cause);
    }

    public IOAdapterException(String message, Throwable cause) {
        super(message, cause);
    }
}
