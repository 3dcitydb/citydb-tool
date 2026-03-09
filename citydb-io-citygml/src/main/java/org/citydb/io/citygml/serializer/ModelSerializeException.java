/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.serializer;

public class ModelSerializeException extends Exception {

    public ModelSerializeException() {
    }

    public ModelSerializeException(String message) {
        super(message);
    }

    public ModelSerializeException(Throwable cause) {
        super(cause);
    }

    public ModelSerializeException(String message, Throwable cause) {
        super(message, cause);
    }
}
