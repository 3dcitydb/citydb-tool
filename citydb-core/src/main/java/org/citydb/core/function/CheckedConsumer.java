/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.core.function;

import java.util.Objects;

@FunctionalInterface
public interface CheckedConsumer<T, E extends Throwable> {
    void accept(T t) throws E;

    default CheckedConsumer<T, E> andThen(CheckedConsumer<? super T, ? extends E> after) {
        Objects.requireNonNull(after);
        return t -> {
            accept(t);
            after.accept(t);
        };
    }
}

