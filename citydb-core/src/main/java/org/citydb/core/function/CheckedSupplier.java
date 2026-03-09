/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.core.function;

import java.util.Objects;

@FunctionalInterface
public interface CheckedSupplier<T, E extends Throwable> {
    T get() throws E;

    default <V> CheckedSupplier<V, E> andThen(CheckedFunction<? super T, ? extends V, ? extends E> after) {
        Objects.requireNonNull(after);
        return () -> after.apply(get());
    }
}
