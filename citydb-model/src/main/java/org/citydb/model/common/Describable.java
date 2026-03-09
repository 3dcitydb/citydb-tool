/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.common;

import java.util.Optional;

public interface Describable<T extends DatabaseDescriptor> {
    Optional<T> getDescriptor();

    Describable<T> setDescriptor(T descriptor);
}
