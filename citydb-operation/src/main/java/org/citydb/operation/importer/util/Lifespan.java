/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.importer.util;

import java.time.OffsetDateTime;
import java.util.Optional;

public class Lifespan {
    public static final Lifespan NONE = new Lifespan(null, null);
    private final OffsetDateTime creationDate;
    private final OffsetDateTime terminationDate;

    private Lifespan(OffsetDateTime creationDate, OffsetDateTime terminationDate) {
        this.creationDate = creationDate;
        this.terminationDate = terminationDate;
    }

    public static Lifespan of(OffsetDateTime creationDate, OffsetDateTime terminationDate) {
        return new Lifespan(creationDate, terminationDate);
    }

    public Optional<OffsetDateTime> getCreationDate() {
        return Optional.ofNullable(creationDate);
    }

    public Optional<OffsetDateTime> getTerminationDate() {
        return Optional.ofNullable(terminationDate);
    }
}
