/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.writer;

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
