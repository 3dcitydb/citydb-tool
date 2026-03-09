/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.util.report;

public class DatabaseReportException extends Exception {

    public DatabaseReportException() {
    }

    public DatabaseReportException(String message) {
        super(message);
    }

    public DatabaseReportException(Throwable cause) {
        super(cause);
    }

    public DatabaseReportException(String message, Throwable cause) {
        super(message, cause);
    }
}
