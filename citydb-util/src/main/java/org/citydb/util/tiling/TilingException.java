/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.util.tiling;

public class TilingException extends Exception {

    public TilingException() {
    }

    public TilingException(String message) {
        super(message);
    }

    public TilingException(Throwable cause) {
        super(cause);
    }

    public TilingException(String message, Throwable cause) {
        super(message, cause);
    }
}
