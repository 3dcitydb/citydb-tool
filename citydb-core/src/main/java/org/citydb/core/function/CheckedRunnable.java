/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.core.function;

@FunctionalInterface
public interface CheckedRunnable<E extends Throwable> {
    void run() throws E;
}
