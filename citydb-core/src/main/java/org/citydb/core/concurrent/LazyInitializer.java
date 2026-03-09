/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.core.concurrent;

import java.util.function.Supplier;

public class LazyInitializer<T> {
    private final Object lock = new Object();
    private final Supplier<T> supplier;
    private volatile boolean initialized = false;
    private T object = null;

    private LazyInitializer(Supplier<T> supplier) {
        this.supplier = supplier;
    }

    public static <T> LazyInitializer<T> of(Supplier<T> supplier) {
        return new LazyInitializer<>(supplier);
    }

    public boolean isInitialized() {
        return initialized;
    }

    public T get() {
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
