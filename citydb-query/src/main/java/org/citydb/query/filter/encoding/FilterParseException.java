/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter.encoding;

public class FilterParseException extends Exception {

    public FilterParseException() {
    }

    public FilterParseException(String message) {
        super(message);
    }

    public FilterParseException(Throwable cause) {
        super(cause);
    }

    public FilterParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
