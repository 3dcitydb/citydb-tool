/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.common;

import org.citydb.model.util.IdCreator;

import java.util.Optional;

public interface Referencable {
    Optional<String> getObjectId();

    Referencable setObjectId(String objectId);

    default String getOrCreateObjectId(IdCreator idCreator) {
        return getObjectId().orElseGet(() -> {
            String objectId = idCreator.createId();
            setObjectId(objectId);
            return objectId;
        });
    }

    default String getOrCreateObjectId() {
        return getOrCreateObjectId(IdCreator.getInstance());
    }
}
