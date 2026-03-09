/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.core.tuple;

public record SimplePair<T>(T first, T second) {
    public static <T> SimplePair<T> of(T first, T second) {
        return new SimplePair<>(first, second);
    }
}
