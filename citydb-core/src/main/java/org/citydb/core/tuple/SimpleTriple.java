/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.core.tuple;

public record SimpleTriple<T>(T first, T second, T third) {
    public static <T> SimpleTriple<T> of(T first, T second, T third) {
        return new SimpleTriple<>(first, second, third);
    }
}
