/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.core.tuple;

public record Quad<T, U, V, W>(T first, U second, V third, W fourth) {
    public static <T, U, V, W> Quad<T, U, V, W> of(T first, U second, V third, W fourth) {
        return new Quad<>(first, second, third, fourth);
    }
}
