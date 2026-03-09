/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.builder;

public class QueryBuildException extends Exception {

    public QueryBuildException() {
    }

    public QueryBuildException(String message) {
        super(message);
    }

    public QueryBuildException(Throwable cause) {
        super(cause);
    }

    public QueryBuildException(String message, Throwable cause) {
        super(message, cause);
    }
}
