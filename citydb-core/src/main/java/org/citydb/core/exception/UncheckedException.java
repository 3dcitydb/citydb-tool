/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.core.exception;

public class UncheckedException extends RuntimeException {

    public UncheckedException(Throwable cause) {
        super(cause);
    }

    public UncheckedException(String message, Throwable cause) {
        super(message, cause);
    }

    public UncheckedException(String message) {
        super(message);
    }

    public static RuntimeException wrap(Throwable t) {
        if (t instanceof RuntimeException e) {
            return e;
        }

        if (t instanceof Error e) {
            throw e;
        }

        return new UncheckedException(t);

    }

    public static <T extends Throwable> T unwrap(Throwable t, Class<T> type) throws T {
        for (Throwable e = t; e != null; e = e.getCause()) {
            if (type.isInstance(e)) {
                throw type.cast(e);
            }
        }

        if (t instanceof RuntimeException e) {
            throw e;
        }

        throw new RuntimeException(t);
    }
}
