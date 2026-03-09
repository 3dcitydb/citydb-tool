/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.core.tuple;

public record SimpleQuad<T>(T first, T second, T third, T fourth) {
    public static <T> SimpleQuad<T> of(T first, T second, T third, T fourth) {
        return new SimpleQuad<>(first, second, third, fourth);
    }
}
