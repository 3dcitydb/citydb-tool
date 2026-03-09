/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.util.csv;

public class IdListException extends Exception {

    public IdListException() {
    }

    public IdListException(String message) {
        super(message);
    }

    public IdListException(Throwable cause) {
        super(cause);
    }

    public IdListException(String message, Throwable cause) {
        super(message, cause);
    }
}
