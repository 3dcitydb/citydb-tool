/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
 * virtualcitysystems GmbH, Germany
 * https://vc.systems/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
