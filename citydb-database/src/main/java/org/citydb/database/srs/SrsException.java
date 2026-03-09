/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.srs;

public class SrsException extends Exception {

    public SrsException() {
    }

    public SrsException(String message) {
        super(message);
    }

    public SrsException(Throwable cause) {
        super(cause);
    }

    public SrsException(String message, Throwable cause) {
        super(message, cause);
    }
}
