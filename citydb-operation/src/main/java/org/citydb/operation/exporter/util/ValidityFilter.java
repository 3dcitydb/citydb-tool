/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.exporter.util;

import org.citydb.database.schema.ValidityReference;
import org.citydb.model.feature.Feature;
import org.citydb.operation.exporter.options.ValidityOptions;

import java.time.OffsetDateTime;
import java.util.Objects;

public class ValidityFilter {
    private final Mode mode;
    private final ValidityReference reference;
    private final OffsetDateTime timestamp;
    private final boolean lenient;

    private enum Mode {
        VALID,
        VALID_AT,
        INVALID,
        INVALID_AT,
        ALL
    }

    public ValidityFilter(ValidityOptions options) {
        Objects.requireNonNull(options, "The validity filter options must not be null.");
        reference = options.getReference();
        timestamp = options.getAt().orElse(null);
        lenient = options.isLenient();
        mode = switch (options.getMode()) {
            case VALID -> timestamp != null ? Mode.VALID_AT : Mode.VALID;
            case INVALID -> timestamp != null ? Mode.INVALID_AT : Mode.INVALID;
            case ALL -> Mode.ALL;
        };
    }

    public boolean filter(Feature feature) {
        return switch (mode) {
            case VALID -> reference.to(feature).isEmpty();
            case VALID_AT -> ((lenient && reference.from(feature).isEmpty())
                    || reference.from(feature).map(from -> !from.isAfter(timestamp)).orElse(false))
                    && (reference.to(feature).isEmpty()
                    || reference.to(feature).map(to -> to.isAfter(timestamp)).orElse(false));
            case INVALID -> reference.to(feature).isPresent();
            case INVALID_AT -> reference.to(feature).map(to -> !to.isAfter(timestamp)).orElse(false);
            case ALL -> true;
        };
    }
}
