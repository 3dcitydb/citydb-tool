/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.core.function;

import java.util.Objects;

@FunctionalInterface
public interface CheckedFunction<T, R, E extends Throwable> {
    R apply(T t) throws E;

    default <V> CheckedFunction<T, V, E> andThen(CheckedFunction<? super R, ? extends V, ? extends E> after) {
        Objects.requireNonNull(after);
        return t -> after.apply(apply(t));
    }
}
