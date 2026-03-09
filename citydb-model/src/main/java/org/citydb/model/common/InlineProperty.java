/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.common;

public interface InlineProperty<T extends Referencable> {
    T getObject();

    InlineProperty<T> setObject(T object);
}
