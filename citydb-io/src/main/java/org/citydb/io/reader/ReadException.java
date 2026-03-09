/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.reader;

public class ReadException extends Exception {

    public ReadException() {
    }

    public ReadException(String message) {
        super(message);
    }

    public ReadException(Throwable cause) {
        super(cause);
    }

    public ReadException(String message, Throwable cause) {
        super(message, cause);
    }
}
