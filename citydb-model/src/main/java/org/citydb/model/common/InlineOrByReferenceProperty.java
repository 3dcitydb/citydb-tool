/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.common;

import java.util.Optional;

public interface InlineOrByReferenceProperty<T extends Referencable, R> {
    Optional<T> getObject();

    InlineOrByReferenceProperty<T, R> setObject(T object);

    Optional<R> getReference();

    InlineOrByReferenceProperty<T, R> setReference(R reference);

    InlineOrByReferenceProperty<T, R> setReference(T referencedObject);
}
