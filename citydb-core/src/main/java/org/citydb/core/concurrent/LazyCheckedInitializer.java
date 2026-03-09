/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.core.concurrent;

import org.citydb.core.function.CheckedSupplier;

public class LazyCheckedInitializer<T, E extends Throwable> {
    private final Object lock = new Object();
    private final CheckedSupplier<T, E> supplier;
    private volatile boolean initialized = false;
    private T object = null;

    private LazyCheckedInitializer(CheckedSupplier<T, E> supplier) {
        this.supplier = supplier;
    }

    public static <T, E extends Exception> LazyCheckedInitializer<T, E> of(CheckedSupplier<T, E> supplier) {
        return new LazyCheckedInitializer<>(supplier);
    }

    public boolean isInitialized() {
        return initialized;
    }

    public T get() throws E {
        if (!initialized) {
            synchronized (lock) {
                if (!initialized) {
                    T result = supplier.get();
                    object = result;
                    initialized = true;
                    return result;
                }
            }
        }

        return object;
    }
}
