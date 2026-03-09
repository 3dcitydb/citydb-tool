/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.geometry;

public class GeometryException extends Exception {

    public GeometryException() {
    }

    public GeometryException(String message) {
        super(message);
    }

    public GeometryException(Throwable cause) {
        super(cause);
    }

    public GeometryException(String message, Throwable cause) {
        super(message, cause);
    }
}
