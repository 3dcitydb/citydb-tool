/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.util.changelog;

public class ChangelogException extends Exception {

    public ChangelogException() {
    }

    public ChangelogException(String message) {
        super(message);
    }

    public ChangelogException(Throwable cause) {
        super(cause);
    }

    public ChangelogException(String message, Throwable cause) {
        super(message, cause);
    }
}
