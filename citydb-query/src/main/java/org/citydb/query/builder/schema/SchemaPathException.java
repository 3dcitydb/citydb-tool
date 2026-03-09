/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.builder.schema;

public class SchemaPathException extends Exception {

    public SchemaPathException() {
    }

    public SchemaPathException(String message) {
        super(message);
    }

    public SchemaPathException(Throwable cause) {
        super(cause);
    }

    public SchemaPathException(String message, Throwable cause) {
        super(message, cause);
    }
}
