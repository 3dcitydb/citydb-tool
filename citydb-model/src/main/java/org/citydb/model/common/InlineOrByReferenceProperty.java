/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.common;

import java.util.Optional;

public interface InlineOrByReferenceProperty<T extends Referencable> {
    Optional<T> getObject();

    InlineOrByReferenceProperty<T> setObject(T object);

    Optional<String> getReference();

    InlineOrByReferenceProperty<T> setReference(String reference);

    InlineOrByReferenceProperty<T> setReference(T referencedObject);
}
