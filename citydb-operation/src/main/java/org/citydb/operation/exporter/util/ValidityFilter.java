/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.exporter.util;

import org.citydb.model.feature.Feature;
import org.citydb.model.feature.Validity;
import org.citydb.model.feature.ValidityReference;

import java.time.OffsetDateTime;

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

    public ValidityFilter(Validity validity) {
        Validity options = validity != null ? validity : Validity.valid();
        reference = options.getReference();
        timestamp = options.getAt().orElse(null);
        lenient = options.isLenient();
        mode = switch (options.getMode()) {
            case VALID -> timestamp != null ? Mode.VALID_AT : Mode.VALID;
            case INVALID -> timestamp != null ? Mode.INVALID_AT : Mode.INVALID;
            case ALL -> Mode.ALL;
        };
    }

    public boolean filter(Feature feature, Feature root) {
        return switch (mode) {
            case VALID -> reference.to(feature).isEmpty();
            case VALID_AT -> ((lenient && reference.from(feature).isEmpty())
                    || reference.from(feature).map(from -> !from.isAfter(timestamp)).orElse(false))
                    && (reference.to(feature).isEmpty()
                    || reference.to(feature).map(to -> to.isAfter(timestamp)).orElse(false));
            case INVALID, INVALID_AT -> isValidAtTermination(feature, root);
            case ALL -> true;
        };
    }

    private boolean isValidAtTermination(Feature feature, Feature root) {
        OffsetDateTime termination = root != null ? reference.to(root).orElse(null) : null;
        return termination != null
                ? reference.to(feature).map(to -> !to.isBefore(termination)).orElse(true)
                : reference.to(feature).isEmpty();
    }
}
