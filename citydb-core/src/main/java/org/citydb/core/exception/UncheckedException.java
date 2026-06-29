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

    public static <A extends Throwable> RuntimeException unwrap(Throwable t, Class<A> a) throws A {
        for (Throwable e = t; e != null; e = e.getCause()) {
            if (a.isInstance(e)) {
                throw a.cast(e);
            }
        }

        if (t instanceof RuntimeException e) {
            throw e;
        }

        throw new UncheckedException(t);
    }

    public static <A extends Throwable, B extends Throwable> RuntimeException unwrap(Throwable t, Class<A> a, Class<B> b) throws A, B {
        for (Throwable e = t; e != null; e = e.getCause()) {
            if (a.isInstance(e)) {
                throw a.cast(e);
            } else if (b.isInstance(e)) {
                throw b.cast(e);
            }
        }

        if (t instanceof RuntimeException e) {
            throw e;
        }

        throw new UncheckedException(t);
    }

    public static <A extends Throwable, B extends Throwable, C extends Throwable> RuntimeException unwrap(Throwable t, Class<A> a, Class<B> b, Class<C> c) throws A, B, C {
        for (Throwable e = t; e != null; e = e.getCause()) {
            if (a.isInstance(e)) {
                throw a.cast(e);
            } else if (b.isInstance(e)) {
                throw b.cast(e);
            } else if (c.isInstance(e)) {
                throw c.cast(e);
            }
        }

        if (t instanceof RuntimeException e) {
            throw e;
        }

        throw new UncheckedException(t);
    }
}
