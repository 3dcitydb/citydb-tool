/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.core.function;

import java.util.Objects;

@FunctionalInterface
public interface CheckedBiFunction<T, U, R, E extends Throwable> {
    R apply(T t, U u) throws E;

    default <V> CheckedBiFunction<T, U, V, E> andThen(CheckedFunction<? super R, ? extends V, ? extends E> after) {
        Objects.requireNonNull(after);
        return (t, u) -> after.apply(apply(t, u));
    }
}
