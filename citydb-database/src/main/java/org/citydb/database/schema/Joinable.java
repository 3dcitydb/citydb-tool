/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.schema;

import java.util.Optional;

public interface Joinable extends SchemaObject {
    Optional<Join> getJoin();

    Optional<JoinTable> getJoinTable();
}
