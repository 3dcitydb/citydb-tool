/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.builder;

public class ModelBuildException extends Exception {

    public ModelBuildException() {
    }

    public ModelBuildException(String message) {
        super(message);
    }

    public ModelBuildException(Throwable cause) {
        super(cause);
    }

    public ModelBuildException(String message, Throwable cause) {
        super(message, cause);
    }
}
