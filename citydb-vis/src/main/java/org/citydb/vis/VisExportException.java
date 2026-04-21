/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis;

public class VisExportException extends Exception {

    public VisExportException() {
    }

    public VisExportException(String message) {
        super(message);
    }

    public VisExportException(Throwable cause) {
        super(cause);
    }

    public VisExportException(String message, Throwable cause) {
        super(message, cause);
    }
}
