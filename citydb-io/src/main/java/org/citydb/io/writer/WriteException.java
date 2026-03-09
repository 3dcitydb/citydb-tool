/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.writer;

public class WriteException extends Exception {

    public WriteException() {
    }

    public WriteException(String message) {
        super(message);
    }

    public WriteException(Throwable cause) {
        super(cause);
    }

    public WriteException(String message, Throwable cause) {
        super(message, cause);
    }
}
