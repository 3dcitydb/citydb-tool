/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.common;

import java.util.Optional;

public interface Identifiable extends Referencable {
    Optional<String> getIdentifier();

    Identifiable setIdentifier(String identifier);

    Optional<String> getIdentifierCodeSpace();

    Identifiable setIdentifierCodeSpace(String identifierCodeSpace);
}
